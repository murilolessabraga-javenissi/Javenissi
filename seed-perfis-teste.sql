-- ============================================================================
-- SEED OPCIONAL — 5 PERFIS FICTÍCIOS PARA TESTE
--
-- Rode DEPOIS do schema.sql, no SQL Editor do Supabase.
-- Cria 5 usuários de teste já aprovados (senha de todos: Senha123!)
-- e algumas indicações entre eles, para você ver a busca funcionando.
--
-- PARA REMOVER depois dos testes, rode:
--   delete from auth.users where email like '%@teste.rede';
-- ============================================================================

-- 1) Usuários no Auth -------------------------------------------------------
insert into auth.users (
  instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
  raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
  confirmation_token, recovery_token, email_change_token_new, email_change
)
select
  '00000000-0000-0000-0000-000000000000',
  t.id::uuid, 'authenticated', 'authenticated', t.email,
  extensions.crypt('Senha123!', extensions.gen_salt('bf')),
  now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb,
  now(), now(), '', '', '', ''
from (values
  ('aaaaaaaa-0000-4000-8000-000000000001', 'maria@teste.rede'),
  ('aaaaaaaa-0000-4000-8000-000000000002', 'joao@teste.rede'),
  ('aaaaaaaa-0000-4000-8000-000000000003', 'ana@teste.rede'),
  ('aaaaaaaa-0000-4000-8000-000000000004', 'carlos@teste.rede'),
  ('aaaaaaaa-0000-4000-8000-000000000005', 'fernanda@teste.rede')
) as t(id, email)
on conflict (id) do nothing;

-- Identidades (necessárias para o Auth reconhecer os usuários de e-mail)
insert into auth.identities (
  id, user_id, provider_id, identity_data, provider,
  last_sign_in_at, created_at, updated_at
)
select
  gen_random_uuid(), u.id, u.id::text,
  jsonb_build_object('sub', u.id::text, 'email', u.email, 'email_verified', true),
  'email', now(), now(), now()
from auth.users u
where u.email like '%@teste.rede'
  and not exists (
    select 1 from auth.identities i where i.user_id = u.id and i.provider = 'email'
  );

-- 2) Perfis (já aprovados) ---------------------------------------------------
insert into public.perfis (
  id, nome_completo, cidade, comunidade_id, descricao, whatsapp, email_contato,
  status, aceitou_lgpd, aceitou_isencao, aceitou_em, anelado
)
select
  t.id::uuid, t.nome, t.cidade,
  (select c.id from public.comunidades c
    where c.cidade = t.cidade and c.nome = t.comunidade limit 1),
  t.descricao, t.whatsapp, t.email,
  'aprovado', true, true, now(),
  -- Maria é anelada, para demonstrar o selo dourado
  t.id = 'aaaaaaaa-0000-4000-8000-000000000001'
from (values
  ('aaaaaaaa-0000-4000-8000-000000000001', 'Maria Aparecida Souza', 'Pouso Alegre', 'Emanuel',
   'Costureira há 20 anos. Faço ajustes, consertos e roupas sob medida. Também aceito encomendas de bolos e doces para festas.',
   '5535999110001', 'maria@teste.rede'),
  ('aaaaaaaa-0000-4000-8000-000000000002', 'João Pedro Oliveira', 'Itajubá', 'Theotokos',
   'Eletricista residencial e predial. Instalações novas, manutenção e laudos. Atendo Itajubá e região.',
   '5535999110002', 'joao@teste.rede'),
  ('aaaaaaaa-0000-4000-8000-000000000003', 'Ana Clara Ferreira', 'Franca', 'Maranathá',
   'Psicóloga clínica (CRP ativo). Atendimento presencial em Franca e online para todo o Brasil.',
   '5516999110003', 'ana@teste.rede'),
  ('aaaaaaaa-0000-4000-8000-000000000004', 'Carlos Eduardo Lima', 'Cambuí', 'Jerusalém',
   'Pedreiro e pintor. Reformas em geral, acabamento fino, orçamento sem compromisso.',
   '5535999110004', 'carlos@teste.rede'),
  ('aaaaaaaa-0000-4000-8000-000000000005', 'Fernanda Ribeiro', 'Santa Rita do Sapucaí', 'Nova Jerusalém',
   'Cabeleireira e manicure. Atendo no salão ou a domicílio. Horários flexíveis, inclusive aos sábados.',
   '5535999110005', 'fernanda@teste.rede')
) as t(id, nome, cidade, comunidade, descricao, whatsapp, email)
on conflict (id) do nothing;

-- 3) Profissões de cada perfil ----------------------------------------------
insert into public.perfil_profissoes (perfil_id, profissao_id)
select t.perfil_id::uuid, p.id
from (values
  ('aaaaaaaa-0000-4000-8000-000000000001', 'Costureira(o)'),
  ('aaaaaaaa-0000-4000-8000-000000000001', 'Confeiteira(o)'),
  ('aaaaaaaa-0000-4000-8000-000000000002', 'Eletricista'),
  ('aaaaaaaa-0000-4000-8000-000000000003', 'Psicólogo(a)'),
  ('aaaaaaaa-0000-4000-8000-000000000004', 'Pedreiro(a)'),
  ('aaaaaaaa-0000-4000-8000-000000000004', 'Pintor(a)'),
  ('aaaaaaaa-0000-4000-8000-000000000005', 'Cabeleireiro(a)'),
  ('aaaaaaaa-0000-4000-8000-000000000005', 'Manicure')
) as t(perfil_id, profissao)
join public.profissoes p on p.nome = t.profissao
on conflict do nothing;

-- 4) Algumas indicações ------------------------------------------------------
insert into public.indicacoes (indicador_id, indicado_id)
select a::uuid, b::uuid from (values
  ('aaaaaaaa-0000-4000-8000-000000000002', 'aaaaaaaa-0000-4000-8000-000000000001'),
  ('aaaaaaaa-0000-4000-8000-000000000003', 'aaaaaaaa-0000-4000-8000-000000000001'),
  ('aaaaaaaa-0000-4000-8000-000000000005', 'aaaaaaaa-0000-4000-8000-000000000001'),
  ('aaaaaaaa-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000002'),
  ('aaaaaaaa-0000-4000-8000-000000000004', 'aaaaaaaa-0000-4000-8000-000000000002'),
  ('aaaaaaaa-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000005')
) as t(a, b)
on conflict do nothing;
