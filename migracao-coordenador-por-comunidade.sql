-- ============================================================================
-- MIGRAÇÃO — COORDENADOR SÓ MODERA A PRÓPRIA COMUNIDADE
--
-- Rode este arquivo no SQL Editor do Supabase (uma vez só) se você já tinha
-- criado o banco antes desta atualização. Instalações novas não precisam:
-- o schema.sql já inclui tudo isto.
--
-- Regra implementada (garantida pelo banco, não só pela tela):
--   * O coordenador só aprova/rejeita/remove/promove perfis da SUA comunidade.
--   * Exceção: se a comunidade do perfil ainda NÃO tem nenhum coordenador,
--     qualquer coordenador pode moderá-la (senão o primeiro membro de uma
--     comunidade nova ficaria preso para sempre na fila).
--   * Perfis sem comunidade definida também podem ser moderados por qualquer
--     coordenador.
-- ============================================================================

-- O usuário "moderador" pode moderar um perfil da comunidade "comunidade_alvo"?
create or replace function public.pode_moderar_perfil(moderador uuid, comunidade_alvo bigint)
returns boolean
language sql stable security definer set search_path = public
as $$
  select exists (
    select 1 from public.perfis m
    where m.id = moderador
      and m.is_coordenador
      and m.status = 'aprovado'
      and (
        comunidade_alvo is null
        or m.comunidade_id = comunidade_alvo
        or not exists (
          select 1 from public.perfis c
          where c.comunidade_id = comunidade_alvo
            and c.is_coordenador
            and c.status = 'aprovado'
        )
      )
  );
$$;

-- Editar (aprovar, rejeitar, promover): o próprio dono ou o coordenador
-- da comunidade do perfil
drop policy if exists "perfis: editar" on public.perfis;
create policy "perfis: editar" on public.perfis
  for update to authenticated
  using (id = auth.uid() or public.pode_moderar_perfil(auth.uid(), comunidade_id));

-- Remover: o próprio dono ou o coordenador da comunidade do perfil
drop policy if exists "perfis: remover" on public.perfis;
create policy "perfis: remover" on public.perfis
  for delete to authenticated
  using (id = auth.uid() or public.pode_moderar_perfil(auth.uid(), comunidade_id));
