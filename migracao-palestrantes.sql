-- ============================================================================
-- MIGRAÇÃO — PALESTRANTES
--
-- Rode este arquivo no SQL Editor do Supabase (uma vez só). Instalações
-- novas não precisam: o schema.sql já inclui tudo isto.
--
-- Regras (garantidas pelo banco):
--   * O membro declara no próprio cadastro se é palestrante de
--     Grupo de Oração e/ou Formação, e registra as datas já comprometidas.
--   * SOMENTE coordenadores e super-usuários enxergam essas informações
--     (ficam em tabelas separadas, invisíveis para os demais membros).
-- ============================================================================

-- Quem é palestrante e de quê
create table if not exists public.palestrantes (
  perfil_id uuid primary key references public.perfis (id) on delete cascade,
  grupo_oracao boolean not null default false,
  formacao boolean not null default false,
  updated_at timestamptz not null default now()
);

-- Datas em que o palestrante já tem compromisso
create table if not exists public.datas_comprometidas (
  id bigint generated always as identity primary key,
  perfil_id uuid not null references public.perfis (id) on delete cascade,
  data date not null,
  unique (perfil_id, data)
);

alter table public.palestrantes enable row level security;
alter table public.datas_comprometidas enable row level security;

-- ---- palestrantes ----
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

-- ---- datas comprometidas ----
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
