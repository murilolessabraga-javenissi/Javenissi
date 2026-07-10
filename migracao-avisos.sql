-- ============================================================================
-- MIGRAÇÃO — MURAL DE AVISOS
--
-- Rode este arquivo no SQL Editor do Supabase (uma vez só). Instalações
-- novas não precisam: o schema.sql já inclui tudo isto.
--
-- Regras (garantidas pelo banco):
--   * COORDENADOR publica avisos para a PRÓPRIA comunidade.
--   * SUPER-USUÁRIO publica avisos PARA TODOS (comunidade_id nulo)
--     ou para a própria comunidade.
--   * Membros aprovados veem os avisos gerais + os da sua comunidade.
--   * Excluir: o autor, o coordenador da comunidade do aviso ou o super.
-- ============================================================================

-- Comunidade do usuário (função de apoio para as políticas)
create or replace function public.minha_comunidade(uid uuid)
returns bigint
language sql stable security definer set search_path = public
as $$
  select comunidade_id from public.perfis where id = uid;
$$;

create table if not exists public.avisos (
  id bigint generated always as identity primary key,
  autor_id uuid references public.perfis (id) on delete cascade,
  -- Nulo = aviso geral (para todos); preenchido = aviso da comunidade
  comunidade_id bigint references public.comunidades (id) on delete cascade,
  mensagem text not null check (char_length(mensagem) <= 500),
  created_at timestamptz not null default now()
);

alter table public.avisos enable row level security;

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
