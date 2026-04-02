CREATE TABLE reservations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    table_id        UUID REFERENCES restaurant_tables(id) ON DELETE SET NULL,
    customer_name   VARCHAR(200) NOT NULL,
    customer_phone  VARCHAR(50),
    party_size      INTEGER NOT NULL,
    reserved_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'BOOKED',
    notes           TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version         INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT chk_reservation_status CHECK (status IN ('BOOKED', 'ARRIVED', 'SEATED', 'CANCELLED', 'NO_SHOW', 'COMPLETED')),
    CONSTRAINT chk_reservation_party_size CHECK (party_size > 0)
);

CREATE INDEX idx_reservations_restaurant_status ON reservations (restaurant_id, status);
CREATE INDEX idx_reservations_restaurant_date ON reservations (restaurant_id, reserved_at);
CREATE INDEX idx_reservations_table ON reservations (table_id) WHERE table_id IS NOT NULL;
