-- V008: Sanctions and PEP screening list with fuzzy matching support

-- Enable pg_trgm for fuzzy string matching
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Sanctions and PEP list
CREATE TABLE shared.sanctions_list (
    id          BIGSERIAL PRIMARY KEY,
    full_name   TEXT NOT NULL,
    list_type   TEXT NOT NULL,  -- 'sanctions', 'pep'
    source      TEXT NOT NULL,  -- 'ofac', 'eu_consolidated', 'un', 'local'
    country     TEXT,
    aliases     TEXT[],
    active      BOOLEAN NOT NULL DEFAULT true,
    added_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_list_type CHECK (list_type IN ('sanctions', 'pep'))
);

CREATE INDEX idx_sanctions_list_trgm ON shared.sanctions_list USING gin(full_name gin_trgm_ops);
CREATE INDEX idx_sanctions_list_type ON shared.sanctions_list(list_type);
CREATE INDEX idx_sanctions_list_active ON shared.sanctions_list(active) WHERE active;

-- Seed fictional entries for testing
INSERT INTO shared.sanctions_list (full_name, list_type, source, country, aliases) VALUES
    ('Darkov Malenko', 'sanctions', 'ofac', 'RU', ARRAY['D. Malenko', 'Darkov M.']),
    ('Viktor Petroshenko', 'sanctions', 'eu_consolidated', 'UA', ARRAY['V. Petroshenko', 'Petroshenko Viktor']),
    ('Nadia Volkov', 'sanctions', 'un', 'RU', ARRAY['N. Volkov', 'Nadia V.']),
    ('Hassan Al-Rashidi', 'sanctions', 'ofac', 'SY', ARRAY['H. Al-Rashidi', 'Al Rashidi Hassan']),
    ('Mikhail Zorenkov', 'sanctions', 'eu_consolidated', 'BY', NULL),
    ('Chen Wei Fang', 'sanctions', 'ofac', 'CN', ARRAY['Wei Fang Chen', 'C.W. Fang']),
    ('Boris Draganov', 'sanctions', 'un', 'RS', ARRAY['B. Draganov']),
    ('Yuri Kovalenko', 'pep', 'local', 'UA', ARRAY['Y. Kovalenko', 'Kovalenko Yuri']),
    ('Fatima El-Mansouri', 'pep', 'eu_consolidated', 'MA', ARRAY['F. El-Mansouri']),
    ('Aleksei Sidorov', 'pep', 'local', 'RU', NULL),
    ('Ibrahim Toure', 'pep', 'un', 'ML', ARRAY['I. Toure', 'Toure Ibrahim']),
    ('Grigori Petrov', 'sanctions', 'ofac', 'RU', ARRAY['G. Petrov', 'Petrov Grigori']),
    ('Yelena Bashkova', 'pep', 'eu_consolidated', 'KZ', ARRAY['Y. Bashkova', 'Bashkova Yelena']),
    ('Rashid Al-Karim', 'sanctions', 'un', 'IQ', ARRAY['R. Al-Karim', 'Al Karim Rashid']),
    ('Tatiana Morozova', 'pep', 'local', 'RU', ARRAY['T. Morozova', 'Morozova Tatiana']);
