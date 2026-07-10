-- ============================================================================
-- MIGRAÇÃO — SUPER-USUÁRIO
--
-- Rode este arquivo no SQL Editor do Supabase (uma vez só). Instalações
-- novas não precisam: o schema.sql já inclui tudo isto.
--
-- Papéis do sistema a partir desta migração:
--   * MEMBRO        cria perfil, busca, indica.
--   * COORDENADOR   aprova/rejeita/remove perfis DA PRÓPRIA COMUNIDADE
--                   e aprova profissões sugeridas.
--   * SUPER-USUÁRIO tudo acima em QUALQUER comunidade + é o único que pode:
--                   - incluir/editar/excluir/aprovar comunidades;
--                   - promover ou rebaixar coordenadores;
--                   - nomear ou revogar outros super-usuários.
--
-- Todas as regras valem no banco (RLS + triggers), não só na tela.
-- No final, este script já promove murilolessabraga@gmail.com a super-usuário.
-- ============================================================================

-- 1) Novo campo -------------------------------------------------------------
alter table public.perfis
  add column if not exists is_super boolean not null default false;

-- 2) Funções de papel --------------------------------------------------------

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

-- Pode moderar um perfil da comunidade indicada?
-- Super-usuário: sempre. Coordenador: só a própria comunidade (ou perfis sem
-- comunidade / de comunidades que ainda não têm coordenador).
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

-- 3) Trigger de proteção dos papéis e do status ------------------------------
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
      -- Passou a se declarar anelado(a): volta para conferência
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

-- 4) Comunidades: gestão exclusiva do super-usuário --------------------------

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

-- 5) Perfis: editar/remover pelo dono, pelo coordenador da comunidade ou
--    pelo super-usuário (já incluso na função pode_moderar_perfil) -----------

drop policy if exists "perfis: editar" on public.perfis;
create policy "perfis: editar" on public.perfis
  for update to authenticated
  using (id = auth.uid() or public.pode_moderar_perfil(auth.uid(), comunidade_id));

drop policy if exists "perfis: remover" on public.perfis;
create policy "perfis: remover" on public.perfis
  for delete to authenticated
  using (id = auth.uid() or public.pode_moderar_perfil(auth.uid(), comunidade_id));

-- 6) Primeiro super-usuário ---------------------------------------------------
-- (o super-usuário já tem todos os poderes de coordenador automaticamente;
--  não é preciso marcá-lo como coordenador)
update public.perfis
set is_super = true, status = 'aprovado'
where id = (select id from auth.users where email = 'murilolessabraga@gmail.com');
