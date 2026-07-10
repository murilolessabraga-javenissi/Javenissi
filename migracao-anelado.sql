-- ============================================================================
-- MIGRAÇÃO — CAMPO "ANELADO(A)"
--
-- Rode este arquivo no SQL Editor do Supabase (uma vez só) se você já tinha
-- criado o banco antes desta atualização. Instalações novas não precisam:
-- o schema.sql já inclui tudo isto.
--
-- O que faz:
--   1. Adiciona a coluna "anelado" nos perfis.
--   2. Atualiza a proteção: se um membro marcar "anelado(a)" depois de já
--      aprovado, o perfil volta para a fila de aprovação (o coordenador
--      confere antes de o selo dourado aparecer).
--   3. Marca a Maria (perfil de teste) como anelada, para você ver o visual.
-- ============================================================================

alter table public.perfis
  add column if not exists anelado boolean not null default false;

create or replace function public.protege_perfil()
returns trigger
language plpgsql security definer set search_path = public
as $$
begin
  if auth.uid() = new.id and not public.eh_coordenador(auth.uid()) then
    if tg_op = 'INSERT' then
      new.status := 'pendente';
      new.motivo_rejeicao := null;
      new.is_coordenador := false;
    else
      new.is_coordenador := old.is_coordenador;
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

-- Perfil de teste: Maria fica anelada para demonstração (não faz nada se os
-- perfis de teste não existirem)
update public.perfis
set anelado = true
where id = 'aaaaaaaa-0000-4000-8000-000000000001';
