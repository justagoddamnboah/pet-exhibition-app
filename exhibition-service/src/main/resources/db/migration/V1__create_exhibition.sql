CREATE TABLE owners (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    age INTEGER NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE pets (
    id UUID PRIMARY KEY,
    pet_name VARCHAR(100) NOT NULL,
    age_months INTEGER,
    sex VARCHAR(6) NOT NULL,
    species VARCHAR(50) NOT NULL,
    owner_id UUID NOT NULL REFERENCES owners(id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_pets_owner_id ON pets(owner_id);