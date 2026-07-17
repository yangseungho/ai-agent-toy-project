-- ============================================================================
--  pgvector 확장 활성화
--
--  RAG 를 위해 PostgreSQL 에 vector 타입이 필요하다.
--  Spring AI 의 PgVectorStore 가 vector_store 테이블과 HNSW 인덱스를 자동 생성하지만
--  (spring.ai.vectorstore.pgvector.initialize-schema=true),
--  확장(extension) 자체는 슈퍼유저 권한이 필요할 수 있어 여기서 먼저 생성한다.
--
--  주의: 일반 postgres 이미지에는 pgvector 가 없다. pgvector/pgvector 이미지를 사용할 것.
-- ============================================================================
CREATE EXTENSION IF NOT EXISTS vector;

-- 벡터 ID 생성을 위해 사용
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
