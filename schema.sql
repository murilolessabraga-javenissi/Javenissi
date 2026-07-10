-- ============================================================================
-- REDE DE SERVIÇOS DA COMUNIDADE — Script de criação do banco (Supabase)
--
-- COMO USAR: no painel do Supabase, abra "SQL Editor", cole este arquivo
-- inteiro e clique em "Run". Pode ser executado mais de uma vez sem problema
-- nas tabelas (usa IF NOT EXISTS), mas o seed só insere se estiver vazio.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. TABELAS
-- ----------------------------------------------------------------------------

-- Comunidades locais (cidade + nome). "aprovada = false" são sugestões de
-- membros aguardando o coordenador.
create table if not exists public.comunidades (
  id bigint generated always as identity primary key,
  cidade text not null,
  uf text not null,
  nome text not null,
  aprovada boolean not null default true,
  sugerida_por uuid references auth.users (id) on delete set null,
  created_at timestamptz not null default now()
);

create unique index if not exists comunidades_unicas
  on public.comunidades (lower(cidade), lower(nome));

-- Profissões. "aprovada = false" são sugestões aguardando o coordenador.
create table if not exists public.profissoes (
  id bigint generated always as identity primary key,
  nome text not null,
  aprovada boolean not null default true,
  sugerida_por uuid references auth.users (id) on delete set null,
  created_at timestamptz not null default now()
);

create unique index if not exists profissoes_unicas
  on public.profissoes (lower(nome));

-- Perfil de cada membro (1 por usuário do Auth).
create table if not exists public.perfis (
  id uuid primary key references auth.users (id) on delete cascade,
  nome_completo text not null,
  foto_url text,
  cidade text not null,
  comunidade_id bigint references public.comunidades (id) on delete set null,
  descricao text check (char_length(descricao) <= 500),
  whatsapp text not null,
  email_contato text not null,
  status text not null default 'pendente'
    check (status in ('pendente', 'aprovado', 'rejeitado')),
  motivo_rejeicao text,
  -- Coordenador: modera perfis da própria comunidade
  is_coordenador boolean not null default false,
  -- Super-usuário: modera qualquer perfil, gerencia comunidades e papéis
  is_super boolean not null default false,
  -- Membro anelado(a) — fez aliança na comunidade; recebe destaque dourado
  anelado boolean not null default false,
  -- Registro dos consentimentos (LGPD e termo de isenção)
  aceitou_lgpd boolean not null default false,
  aceitou_isencao boolean not null default false,
  aceitou_em timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- Profissões de cada perfil (máximo 3, garantido por trigger).
create table if not exists public.perfil_profissoes (
  perfil_id uuid not null references public.perfis (id) on delete cascade,
  profissao_id bigint not null references public.profissoes (id) on delete cascade,
  primary key (perfil_id, profissao_id)
);

-- Palestrantes: o membro se declara palestrante (Grupo de Oração e/ou
-- Formação) e registra datas comprometidas. Visível SÓ para coordenadores.
create table if not exists public.palestrantes (
  perfil_id uuid primary key references public.perfis (id) on delete cascade,
  grupo_oracao boolean not null default false,
  formacao boolean not null default false,
  updated_at timestamptz not null default now()
);

create table if not exists public.datas_comprometidas (
  id bigint generated always as identity primary key,
  perfil_id uuid not null references public.perfis (id) on delete cascade,
  data date not null,
  unique (perfil_id, data)
);

-- Mural de avisos: coordenador publica para a própria comunidade;
-- super-usuário publica para todos (comunidade_id nulo).
create table if not exists public.avisos (
  id bigint generated always as identity primary key,
  autor_id uuid references public.perfis (id) on delete cascade,
  comunidade_id bigint references public.comunidades (id) on delete cascade,
  mensagem text not null check (char_length(mensagem) <= 500),
  created_at timestamptz not null default now()
);

-- Indicações "Eu indico" (sem estrelas nem comentários).
create table if not exists public.indicacoes (
  indicador_id uuid not null references public.perfis (id) on delete cascade,
  indicado_id uuid not null references public.perfis (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (indicador_id, indicado_id),
  constraint nao_indicar_a_si_mesmo check (indicador_id <> indicado_id)
);

-- ----------------------------------------------------------------------------
-- 2. FUNÇÕES DE APOIO (usadas nas políticas de segurança)
-- ----------------------------------------------------------------------------

-- O usuário é um membro aprovado?
create or replace function public.eh_aprovado(uid uuid)
returns boolean
language sql stable security definer set search_path = public
as $$
  select exists (
    select 1 from public.perfis
    where id = uid and status = 'aprovado'
  );
$$;

-- Comunidade do usuário (usada nas políticas do mural de avisos)
create or replace function public.minha_comunidade(uid uuid)
returns bigint
language sql stable security definer set search_path = public
as $$
  select comunidade_id from public.perfis where id = uid;
$$;

-- O usuário é super-usuário (aprovado)?
create or replace function public.eh_super(uid uuid)
returns boolean
language sql stable security definer set search_path = public
as $$
  select exists (
    select 1 from public.perfis
    where id = uid and status = 'aprovado' and is_super
  );
$$;

-- O usuário tem poderes de coordenador? (super-usuário também tem)
create or replace function public.eh_coordenador(uid uuid)
returns boolean
language sql stable security definer set search_path = public
as $$
  select exists (
    select 1 from public.perfis
    where id = uid and status = 'aprovado' and (is_coordenador or is_super)
  );
$$;

-- O usuário "moderador" pode moderar um perfil da comunidade "comunidade_alvo"?
-- Super-usuário: sempre. Coordenador: só a própria comunidade (ou perfis sem
-- comunidade / de comunidades que ainda não têm nenhum coordenador).
create or replace function public.pode_moderar_perfil(moderador uuid, comunidade_alvo bigint)
returns boolean
language sql stable security definer set search_path = public
as $$
  select exists (
    select 1 from public.perfis m
    where m.id = moderador
      and m.status = 'aprovado'
      and (m.is_coordenador or m.is_super)
      and (
        m.is_super
        or comunidade_alvo is null
        or m.comunidade_id = comunidade_alvo
        or not exists (
          select 1 from public.perfis c
          where c.comunidade_id = comunidade_alvo
            and c.status = 'aprovado'
            and (c.is_coordenador or c.is_super)
        )
      )
  );
$$;

-- ----------------------------------------------------------------------------
-- 3. TRIGGERS DE PROTEÇÃO
-- ----------------------------------------------------------------------------

-- Protege papéis e status:
--   * só super-usuários alteram is_coordenador / is_super de quem quer que seja;
--   * membro comum não altera o próprio status de aprovação;
--   * perfil rejeitado editado pelo dono volta para a fila como "pendente".
create or replace function public.protege_perfil()
returns trigger
language plpgsql security definer set search_path = public
as $$
begin
  -- Somente super-usuários alteram papéis (coordenador / super).
  -- Comandos do SQL Editor (auth.uid() nulo) passam livres.
  if auth.uid() is not null and not public.eh_super(auth.uid()) then
    if tg_op = 'INSERT' then
      new.is_coordenador := false;
      new.is_super := false;
    else
      new.is_coordenador := old.is_coordenador;
      new.is_super := old.is_super;
    end if;
  end if;

  -- Membro comum não altera o próprio status de aprovação
  if auth.uid() = new.id and not public.eh_coordenador(auth.uid()) then
    if tg_op = 'INSERT' then
      new.status := 'pendente';
      new.motivo_rejeicao := null;
    else
      if old.status = 'rejeitado' then
        -- Reenvio após correção: volta para a fila de aprovação
        new.status := 'pendente';
        new.motivo_rejeicao := null;
      else
        new.status := old.status;
        new.motivo_rejeicao := old.motivo_rejeicao;
      end if;
      -- Passou a se declarar anelado(a): volta para conferência do coordenador
      if new.anelado and not old.anelado then
        new.status := 'pendente';
        new.motivo_rejeicao := null;
      end if;
    end if;
  end if;

  if tg_op = 'UPDATE' then
    new.updated_at := now();
  end if;
  return new;
end;
$$;

drop trigger if exists trg_protege_perfil on public.perfis;
create trigger trg_protege_perfil
  before insert or update on public.perfis
  for each row execute function public.protege_perfil();

-- Garante no máximo 3 profissões por perfil.
create or replace function public.limita_profissoes()
returns trigger
language plpgsql security definer set search_path = public
as $$
begin
  if (select count(*) from public.perfil_profissoes where perfil_id = new.perfil_id) >= 3 then
    raise exception 'Um perfil pode ter no máximo 3 profissões.';
  end if;
  return new;
end;
$$;

drop trigger if exists trg_limita_profissoes on public.perfil_profissoes;
create trigger trg_limita_profissoes
  before insert on public.perfil_profissoes
  for each row execute function public.limita_profissoes();

-- ----------------------------------------------------------------------------
-- 4. EXCLUSÃO DE CONTA (LGPD — apaga TUDO do usuário, inclusive o login)
-- ----------------------------------------------------------------------------

create or replace function public.excluir_minha_conta()
returns void
language plpgsql security definer set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'É preciso estar logado.';
  end if;
  -- Remove os arquivos de foto do usuário
  delete from storage.objects
  where bucket_id = 'fotos'
    and (storage.foldername(name))[1] = auth.uid()::text;
  -- Apaga o usuário do Auth; o perfil, profissões e indicações caem em cascata
  delete from auth.users where id = auth.uid();
end;
$$;

revoke all on function public.excluir_minha_conta() from public;
grant execute on function public.excluir_minha_conta() to authenticated;

-- ----------------------------------------------------------------------------
-- 5. SEGURANÇA (Row Level Security)
--    Regra geral: nada é visível sem login; contatos só para membros
--    aprovados; coordenadores enxergam e administram tudo.
-- ----------------------------------------------------------------------------

alter table public.comunidades enable row level security;
alter table public.profissoes enable row level security;
alter table public.perfis enable row level security;
alter table public.perfil_profissoes enable row level security;
alter table public.indicacoes enable row level security;
alter table public.avisos enable row level security;
alter table public.palestrantes enable row level security;
alter table public.datas_comprometidas enable row level security;

-- ---- comunidades ----
drop policy if exists "comunidades: ver" on public.comunidades;
create policy "comunidades: ver" on public.comunidades
  for select to authenticated
  using (aprovada or sugerida_por = auth.uid() or public.eh_coordenador(auth.uid()));

-- Qualquer membro pode SUGERIR; só o super-usuário inclui/edita/exclui/aprova
drop policy if exists "comunidades: sugerir" on public.comunidades;
create policy "comunidades: sugerir" on public.comunidades
  for insert to authenticated
  with check (
    (not aprovada and sugerida_por = auth.uid())
    or public.eh_super(auth.uid())
  );

drop policy if exists "comunidades: administrar (update)" on public.comunidades;
create policy "comunidades: administrar (update)" on public.comunidades
  for update to authenticated
  using (public.eh_super(auth.uid()));

drop policy if exists "comunidades: administrar (delete)" on public.comunidades;
create policy "comunidades: administrar (delete)" on public.comunidades
  for delete to authenticated
  using (public.eh_super(auth.uid()));

-- ---- profissoes ----
drop policy if exists "profissoes: ver" on public.profissoes;
create policy "profissoes: ver" on public.profissoes
  for select to authenticated
  using (aprovada or sugerida_por = auth.uid() or public.eh_coordenador(auth.uid()));

drop policy if exists "profissoes: sugerir" on public.profissoes;
create policy "profissoes: sugerir" on public.profissoes
  for insert to authenticated
  with check (
    (not aprovada and sugerida_por = auth.uid())
    or public.eh_coordenador(auth.uid())
  );

drop policy if exists "profissoes: administrar (update)" on public.profissoes;
create policy "profissoes: administrar (update)" on public.profissoes
  for update to authenticated
  using (public.eh_coordenador(auth.uid()));

drop policy if exists "profissoes: administrar (delete)" on public.profissoes;
create policy "profissoes: administrar (delete)" on public.profissoes
  for delete to authenticated
  using (public.eh_coordenador(auth.uid()));

-- ---- perfis ----
-- Ver: o próprio perfil sempre; os demais só se AMBOS forem aprovados;
-- coordenador vê todos. Sem login não se vê nada (RLS bloqueia anon).
drop policy if exists "perfis: ver" on public.perfis;
create policy "perfis: ver" on public.perfis
  for select to authenticated
  using (
    id = auth.uid()
    or public.eh_coordenador(auth.uid())
    or (status = 'aprovado' and public.eh_aprovado(auth.uid()))
  );

drop policy if exists "perfis: criar o proprio" on public.perfis;
create policy "perfis: criar o proprio" on public.perfis
  for insert to authenticated
  with check (id = auth.uid());

-- Editar (aprovar, rejeitar, promover): o dono ou o coordenador da comunidade
drop policy if exists "perfis: editar" on public.perfis;
create policy "perfis: editar" on public.perfis
  for update to authenticated
  using (id = auth.uid() or public.pode_moderar_perfil(auth.uid(), comunidade_id));

drop policy if exists "perfis: remover" on public.perfis;
create policy "perfis: remover" on public.perfis
  for delete to authenticated
  using (id = auth.uid() or public.pode_moderar_perfil(auth.uid(), comunidade_id));

-- ---- perfil_profissoes ----
drop policy if exists "perfil_profissoes: ver" on public.perfil_profissoes;
create policy "perfil_profissoes: ver" on public.perfil_profissoes
  for select to authenticated
  using (
    perfil_id = auth.uid()
    or public.eh_coordenador(auth.uid())
    or public.eh_aprovado(auth.uid())
  );

drop policy if exists "perfil_profissoes: gerenciar as proprias (insert)" on public.perfil_profissoes;
create policy "perfil_profissoes: gerenciar as proprias (insert)" on public.perfil_profissoes
  for insert to authenticated
  with check (perfil_id = auth.uid());

drop policy if exists "perfil_profissoes: gerenciar as proprias (delete)" on public.perfil_profissoes;
create policy "perfil_profissoes: gerenciar as proprias (delete)" on public.perfil_profissoes
  for delete to authenticated
  using (perfil_id = auth.uid() or public.eh_coordenador(auth.uid()));

-- ---- indicacoes ----
drop policy if exists "indicacoes: ver" on public.indicacoes;
create policy "indicacoes: ver" on public.indicacoes
  for select to authenticated
  using (public.eh_aprovado(auth.uid()) or public.eh_coordenador(auth.uid()));

drop policy if exists "indicacoes: indicar" on public.indicacoes;
create policy "indicacoes: indicar" on public.indicacoes
  for insert to authenticated
  with check (
    indicador_id = auth.uid()
    and public.eh_aprovado(auth.uid())
    and public.eh_aprovado(indicado_id)
  );

drop policy if exists "indicacoes: retirar" on public.indicacoes;
create policy "indicacoes: retirar" on public.indicacoes
  for delete to authenticated
  using (indicador_id = auth.uid() or public.eh_coordenador(auth.uid()));

-- ---- avisos ----
drop policy if exists "avisos: ver" on public.avisos;
create policy "avisos: ver" on public.avisos
  for select to authenticated
  using (
    autor_id = auth.uid()
    or (
      public.eh_aprovado(auth.uid())
      and (
        comunidade_id is null
        or comunidade_id = public.minha_comunidade(auth.uid())
      )
    )
    or public.eh_super(auth.uid())
  );

drop policy if exists "avisos: publicar" on public.avisos;
create policy "avisos: publicar" on public.avisos
  for insert to authenticated
  with check (
    autor_id = auth.uid()
    and (
      -- Aviso geral: só super-usuário
      (comunidade_id is null and public.eh_super(auth.uid()))
      -- Aviso da própria comunidade: coordenador (ou super)
      or (
        comunidade_id = public.minha_comunidade(auth.uid())
        and public.eh_coordenador(auth.uid())
      )
    )
  );

drop policy if exists "avisos: excluir" on public.avisos;
create policy "avisos: excluir" on public.avisos
  for delete to authenticated
  using (
    autor_id = auth.uid()
    or public.eh_super(auth.uid())
    or (
      comunidade_id is not null
      and comunidade_id = public.minha_comunidade(auth.uid())
      and public.eh_coordenador(auth.uid())
    )
  );

-- ---- palestrantes (visível só para coordenadores) ----
drop policy if exists "palestrantes: ver" on public.palestrantes;
create policy "palestrantes: ver" on public.palestrantes
  for select to authenticated
  using (perfil_id = auth.uid() or public.eh_coordenador(auth.uid()));

drop policy if exists "palestrantes: declarar" on public.palestrantes;
create policy "palestrantes: declarar" on public.palestrantes
  for insert to authenticated
  with check (perfil_id = auth.uid());

drop policy if exists "palestrantes: atualizar" on public.palestrantes;
create policy "palestrantes: atualizar" on public.palestrantes
  for update to authenticated
  using (perfil_id = auth.uid());

drop policy if exists "palestrantes: retirar" on public.palestrantes;
create policy "palestrantes: retirar" on public.palestrantes
  for delete to authenticated
  using (perfil_id = auth.uid());

-- ---- datas comprometidas dos palestrantes ----
drop policy if exists "datas: ver" on public.datas_comprometidas;
create policy "datas: ver" on public.datas_comprometidas
  for select to authenticated
  using (perfil_id = auth.uid() or public.eh_coordenador(auth.uid()));

drop policy if exists "datas: adicionar" on public.datas_comprometidas;
create policy "datas: adicionar" on public.datas_comprometidas
  for insert to authenticated
  with check (perfil_id = auth.uid());

drop policy if exists "datas: remover" on public.datas_comprometidas;
create policy "datas: remover" on public.datas_comprometidas
  for delete to authenticated
  using (perfil_id = auth.uid());

-- ----------------------------------------------------------------------------
-- 6. ARMAZENAMENTO DE FOTOS (bucket público, cada usuário grava só na
--    própria pasta)
-- ----------------------------------------------------------------------------

insert into storage.buckets (id, name, public)
values ('fotos', 'fotos', true)
on conflict (id) do nothing;

drop policy if exists "fotos: leitura" on storage.objects;
create policy "fotos: leitura" on storage.objects
  for select using (bucket_id = 'fotos');

drop policy if exists "fotos: enviar na propria pasta" on storage.objects;
create policy "fotos: enviar na propria pasta" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'fotos' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "fotos: atualizar na propria pasta" on storage.objects;
create policy "fotos: atualizar na propria pasta" on storage.objects
  for update to authenticated
  using (bucket_id = 'fotos' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "fotos: apagar na propria pasta" on storage.objects;
create policy "fotos: apagar na propria pasta" on storage.objects
  for delete to authenticated
  using (bucket_id = 'fotos' and (storage.foldername(name))[1] = auth.uid()::text);

-- ----------------------------------------------------------------------------
-- 7. SEED — PROFISSÕES (~40 comuns no Brasil)
-- ----------------------------------------------------------------------------

insert into public.profissoes (nome)
select nome from (values
  ('Advogado(a)'), ('Médico(a)'), ('Dentista'), ('Eletricista'), ('Encanador(a)'),
  ('Pedreiro(a)'), ('Pintor(a)'), ('Costureira(o)'), ('Cabeleireiro(a)'), ('Manicure'),
  ('Contador(a)'), ('Professor(a) particular'), ('Psicólogo(a)'), ('Nutricionista'),
  ('Personal trainer'), ('Fotógrafo(a)'), ('Designer'), ('Desenvolvedor(a)'),
  ('Mecânico(a)'), ('Motorista'), ('Diarista'), ('Cozinheira(o)'), ('Confeiteira(o)'),
  ('Jardineiro(a)'), ('Arquiteto(a)'), ('Engenheiro(a) civil'), ('Corretor(a) de imóveis'),
  ('Veterinário(a)'), ('Fisioterapeuta'), ('Marceneiro(a)'), ('Serralheiro(a)'),
  ('Técnico(a) de informática'), ('Eletricista automotivo'), ('Montador(a) de móveis'),
  ('Babá'), ('Cuidador(a) de idosos'), ('DJ/Músico para eventos'), ('Decorador(a)'),
  ('Salgadeira(o)'), ('Chaveiro')
) as t(nome)
where not exists (select 1 from public.profissoes);

-- ----------------------------------------------------------------------------
-- 8. SEED — COMUNIDADES (79)
-- ----------------------------------------------------------------------------

insert into public.comunidades (cidade, uf, nome)
select cidade, uf, nome from (values
  -- Arquidiocese de Pouso Alegre / MG
  ('Albertina', 'MG', 'Divina Luz'),
  ('Andradas', 'MG', 'Betânia'),
  ('Bom Repouso', 'MG', 'Magnificat'),
  ('Borda da Mata', 'MG', 'Vaso Novo'),
  ('Brasópolis', 'MG', 'Maranathá'),
  ('Brasópolis (Bairro Bom Sucesso)', 'MG', 'Filhos de Maria'),
  ('Bueno Brandão', 'MG', 'Senhor Bom Jesus'),
  ('Bueno Brandão', 'MG', 'Cenáculo'),
  ('Cachoeira de Minas', 'MG', 'Rainha da Paz'),
  ('Caldas', 'MG', 'Deus Proverá'),
  ('Camanducaia', 'MG', 'Santa Maria'),
  ('Cambuí', 'MG', 'Jerusalém'),
  ('Cambuí (Bairro Congonhal)', 'MG', 'Exército de Adoradores'),
  ('Cambuí (Bairro dos Lopes)', 'MG', 'Rainha da Paz'),
  ('Carvalhópolis', 'MG', 'Bento XVI'),
  ('Conceição dos Ouros', 'MG', 'Nova Aliança'),
  ('Congonhal', 'MG', 'São Rafael'),
  ('Consolação', 'MG', 'Nossa Senhora da Consolação'),
  ('Córrego do Bom Jesus (Campos do Raposo)', 'MG', 'Monte Horebe'),
  ('Crisólia (Ouro Fino)', 'MG', 'Jerusalém'),
  ('Delfim Moreira', 'MG', 'Ave Maria'),
  ('Espírito Santo do Dourado', 'MG', 'Espírito Santo'),
  ('Estiva', 'MG', 'Nossa Senhora Aparecida'),
  ('Estiva (Bairro Córrego dos Mulatos)', 'MG', 'Nossa Senhora da Luz'),
  ('Estiva (Bairro Pinhal 2)', 'MG', 'Nossa Senhora das Graças'),
  ('Estiva (Bairro Grotinha)', 'MG', 'Bom Jesus'),
  ('Estiva (Bairro Pinhal 1)', 'MG', 'Nossa Senhora de Fátima'),
  ('Estiva (Bairro Ribeirão das Pedras)', 'MG', 'Porta Para o Céu'),
  ('Estiva (Bairro Pantano dos Teodoros)', 'MG', 'Mãe de Deus'),
  ('Extrema', 'MG', 'Tempo Novo'),
  ('Extrema', 'MG', 'Nova Jerusalém'),
  ('Inconfidentes', 'MG', 'Aliança'),
  ('Inconfidentes (Bairro Pinhalzinho dos Góes)', 'MG', 'Coração de Jesus'),
  ('Ipuiúna', 'MG', 'Nova Jerusalém'),
  ('Ipuiúna (Bairro Moreiras)', 'MG', 'Sagrado Coração de Jesus'),
  ('Itajubá', 'MG', 'Theotokos'),
  ('Itapeva', 'MG', 'Medalha Milagrosa'),
  ('Itapeva (Bairro Areias)', 'MG', 'Nossa Senhora Aparecida'),
  ('Jacutinga', 'MG', 'Nação Santa'),
  ('Luminosa (Distrito de Brasópolis)', 'MG', 'Vida no Espírito'),
  ('Maria da Fé', 'MG', 'Kairós'),
  ('Marmelópolis', 'MG', 'Sagrada Família'),
  ('Monte Sião', 'MG', 'Porta Formosa'),
  ('Monte Sião (Bairro Mococa)', 'MG', 'Dom Ricardo Pedro'),
  ('Monte Verde (Camanducaia)', 'MG', 'Sagrado Coração de Jesus'),
  ('Munhoz', 'MG', 'São Paulo Apóstolo'),
  ('Munhoz (Ribeirão Fundo)', 'MG', 'Imaculada Conceição'),
  ('Ouro Fino', 'MG', 'Amigos de Jesus'),
  ('Ouro Fino', 'MG', 'Porta do Céu'),
  ('Paraisópolis', 'MG', 'Betel'),
  ('Paraisópolis (Distrito dos Costas)', 'MG', 'Monte Tabor'),
  ('Piranguinho', 'MG', 'Saluz'),
  ('Piranguinho (Santa Bárbara)', 'MG', 'São Miguel Arcanjo'),
  ('Poço Fundo', 'MG', 'Mãe da Divina Providência'),
  ('Pouso Alegre', 'MG', 'Emanuel'),
  ('Pouso Alegre', 'MG', 'João XXIII'),
  ('Pouso Alegre', 'MG', 'Bom Pastor'),
  ('Pouso Alegre', 'MG', 'Sagrada Família'),
  ('Pouso Alegre', 'MG', 'Nova Jerusalém'),
  ('Pouso Alegre', 'MG', 'Divina Providência'),
  ('Pouso Alegre', 'MG', 'São Miguel Arcanjo'),
  ('Pouso Alegre', 'MG', 'Ágape'),
  ('Pouso Alegre', 'MG', 'Divina Luz'),
  ('Pouso Alegre', 'MG', 'São João Paulo II'),
  ('Pouso Alegre', 'MG', 'Nossa Senhora de Nazaré'),
  ('Santa Rita do Sapucaí', 'MG', 'Nova Jerusalém'),
  ('Santa Rita de Caldas', 'MG', 'Cheios da Graça'),
  ('São Bento de Caldas (Santa Rita de Caldas)', 'MG', 'Água Viva'),
  ('São João da Mata', 'MG', 'Doce Coração de Maria'),
  ('Sapucaí-Mirim', 'MG', 'Senhora Desatadora dos Nós'),
  ('Senador Amaral', 'MG', 'Jesus Cristo é o Senhor'),
  ('Senador Amaral (Bairro Três Saltos)', 'MG', 'Ranchinho'),
  ('Senador Amaral (Bairro Políçias)', 'MG', 'Senhora Desatadora dos Nós'),
  ('Senador Amaral (Bairro Campo Belo)', 'MG', 'São Gabriel'),
  ('Tocos do Moji', 'MG', 'Nossa Senhora Aparecida'),
  ('Toledo', 'MG', 'Rainha da Paz'),
  ('Toledo (Bairro Pereiras)', 'MG', 'Nossa Senhora Auxiliadora'),
  ('Turvolândia', 'MG', 'Karitomene'),
  -- Diocese de Franca / SP
  ('Franca', 'SP', 'Maranathá'),
  ('Franca', 'SP', 'Santa Luzia')
) as t(cidade, uf, nome)
where not exists (select 1 from public.comunidades);

-- ============================================================================
-- FIM. Próximos passos (veja o README):
--   1. Rode também o arquivo seed-perfis-teste.sql se quiser 5 perfis de teste.
--   2. Crie sua conta pelo aplicativo e depois promova-a a SUPER-USUÁRIO com:
--      update public.perfis
--      set is_super = true, status = 'aprovado'
--      where id = (select id from auth.users where email = 'SEU-EMAIL-AQUI');
--   Papéis: membro < coordenador (modera a própria comunidade) <
--   super-usuário (modera tudo, gerencia comunidades e nomeia papéis).
-- ============================================================================
