-- ============================================================================
-- SEMHAS PERSISTENT REAL-TIME ANALYTICS & HISTORICAL BILLING MIGRATION
-- Project: SEMHAS (Smart Energy Monitoring & Home Automation System)
-- ============================================================================

-- 1. HISTORICAL INTERVAL ENERGY READINGS TABLE
-- Stores interval delta consumption rather than cumulative boot-local values.
-- Even when a channel is turned OFF, telemetry continues (power=0, delta=0).
CREATE TABLE IF NOT EXISTS public.energy_readings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appliance_id UUID NOT NULL REFERENCES public.appliances(id) ON DELETE CASCADE,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    voltage NUMERIC(8, 3) NOT NULL DEFAULT 0.000,
    current NUMERIC(8, 3) NOT NULL DEFAULT 0.000,
    power NUMERIC(8, 3) NOT NULL DEFAULT 0.000,
    energy_delta_kwh NUMERIC(14, 8) NOT NULL DEFAULT 0.00000000,
    runtime_delta_seconds NUMERIC(10, 3) NOT NULL DEFAULT 0.000,
    estimated_cost NUMERIC(12, 4) NOT NULL DEFAULT 0.0000,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Performance indices for energy_readings
CREATE INDEX IF NOT EXISTS idx_energy_readings_appliance_id ON public.energy_readings(appliance_id);
CREATE INDEX IF NOT EXISTS idx_energy_readings_recorded_at ON public.energy_readings(recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_energy_readings_appliance_recorded ON public.energy_readings(appliance_id, recorded_at DESC);

-- 2. DAILY AGGREGATION TABLE
-- Persistent daily consumption totals per appliance.
-- Never resets when an appliance is switched OFF or when ESP32 reboots.
CREATE TABLE IF NOT EXISTS public.daily_consumption (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appliance_id UUID NOT NULL REFERENCES public.appliances(id) ON DELETE CASCADE,
    consumption_date DATE NOT NULL,
    energy_kwh NUMERIC(14, 8) NOT NULL DEFAULT 0.00000000,
    runtime_seconds NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    estimated_cost NUMERIC(12, 4) NOT NULL DEFAULT 0.0000,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_daily_consumption_appliance_date UNIQUE (appliance_id, consumption_date)
);

-- Performance indices for daily_consumption
CREATE INDEX IF NOT EXISTS idx_daily_consumption_date ON public.daily_consumption(consumption_date);
CREATE INDEX IF NOT EXISTS idx_daily_consumption_appliance ON public.daily_consumption(appliance_id);
CREATE INDEX IF NOT EXISTS idx_daily_consumption_appliance_date ON public.daily_consumption(appliance_id, consumption_date);

-- 3. AGGREGATION TRIGGER FUNCTION
-- Automatically accumulates incoming energy_delta_kwh into daily_consumption
-- and maintains live_readings table for real-time monitoring.
CREATE OR REPLACE FUNCTION public.fn_aggregate_energy_reading()
RETURNS TRIGGER AS $$
DECLARE
    rec_date DATE;
    v_total_energy NUMERIC(14, 8);
    v_total_runtime BIGINT;
    v_total_cost NUMERIC(12, 4);
BEGIN
    -- Determine local consumption date using Asia/Kolkata timezone (IST)
    rec_date := (NEW.recorded_at AT TIME ZONE 'Asia/Kolkata')::DATE;

    -- Upsert interval deltas into daily_consumption
    INSERT INTO public.daily_consumption (
        appliance_id,
        consumption_date,
        energy_kwh,
        runtime_seconds,
        estimated_cost,
        updated_at
    )
    VALUES (
        NEW.appliance_id,
        rec_date,
        COALESCE(NEW.energy_delta_kwh, 0.00000000),
        COALESCE(NEW.runtime_delta_seconds, 0.00),
        COALESCE(NEW.estimated_cost, 0.0000),
        NOW()
    )
    ON CONFLICT (appliance_id, consumption_date)
    DO UPDATE SET
        energy_kwh = public.daily_consumption.energy_kwh + EXCLUDED.energy_kwh,
        runtime_seconds = public.daily_consumption.runtime_seconds + EXCLUDED.runtime_seconds,
        estimated_cost = public.daily_consumption.estimated_cost + EXCLUDED.estimated_cost,
        updated_at = NOW();

    -- Calculate all-time persistent totals for this appliance
    SELECT 
        COALESCE(SUM(energy_kwh), 0.00000000),
        COALESCE(SUM(runtime_seconds), 0)::BIGINT,
        COALESCE(SUM(estimated_cost), 0.0000)
    INTO 
        v_total_energy,
        v_total_runtime,
        v_total_cost
    FROM public.daily_consumption
    WHERE appliance_id = NEW.appliance_id;

    -- Synchronize existing live_readings table with live current/power + persistent energy
    INSERT INTO public.live_readings (
        appliance_id,
        voltage,
        current,
        power,
        energy,
        runtime_seconds,
        estimated_cost,
        recorded_at
    )
    VALUES (
        NEW.appliance_id,
        NEW.voltage,
        NEW.current,
        NEW.power,
        v_total_energy,
        v_total_runtime,
        v_total_cost,
        NEW.recorded_at
    );

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Bind trigger to energy_readings
DROP TRIGGER IF EXISTS trg_energy_reading_inserted ON public.energy_readings;
CREATE TRIGGER trg_energy_reading_inserted
AFTER INSERT ON public.energy_readings
FOR EACH ROW
EXECUTE FUNCTION public.fn_aggregate_energy_reading();

-- 4. MIGRATE / PRESERVE EXISTING HISTORICAL DATA (Requirement 17)
-- If any existing live_readings rows exist, ensure they populate daily_consumption.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'live_readings') THEN
        INSERT INTO public.daily_consumption (appliance_id, consumption_date, energy_kwh, runtime_seconds, estimated_cost, updated_at)
        SELECT 
            appliance_id,
            (recorded_at AT TIME ZONE 'Asia/Kolkata')::DATE AS consumption_date,
            MAX(energy) AS energy_kwh,
            MAX(runtime_seconds) AS runtime_seconds,
            MAX(estimated_cost) AS estimated_cost,
            NOW() AS updated_at
        FROM public.live_readings
        WHERE energy > 0
        GROUP BY appliance_id, (recorded_at AT TIME ZONE 'Asia/Kolkata')::DATE
        ON CONFLICT (appliance_id, consumption_date)
        DO UPDATE SET
            energy_kwh = GREATEST(public.daily_consumption.energy_kwh, EXCLUDED.energy_kwh),
            runtime_seconds = GREATEST(public.daily_consumption.runtime_seconds, EXCLUDED.runtime_seconds),
            estimated_cost = GREATEST(public.daily_consumption.estimated_cost, EXCLUDED.estimated_cost),
            updated_at = NOW();
    END IF;
END $$;

-- 5. ROW LEVEL SECURITY (RLS) POLICIES (Requirement 19)
ALTER TABLE public.energy_readings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.daily_consumption ENABLE ROW LEVEL SECURITY;

-- Allow anon and authenticated access for read and write
DROP POLICY IF EXISTS "Allow anon and auth read energy_readings" ON public.energy_readings;
CREATE POLICY "Allow anon and auth read energy_readings"
ON public.energy_readings FOR SELECT
TO anon, authenticated
USING (true);

DROP POLICY IF EXISTS "Allow anon and auth insert energy_readings" ON public.energy_readings;
CREATE POLICY "Allow anon and auth insert energy_readings"
ON public.energy_readings FOR INSERT
TO anon, authenticated
WITH CHECK (true);

DROP POLICY IF EXISTS "Allow anon and auth select daily_consumption" ON public.daily_consumption;
CREATE POLICY "Allow anon and auth select daily_consumption"
ON public.daily_consumption FOR SELECT
TO anon, authenticated
USING (true);

DROP POLICY IF EXISTS "Allow anon and auth insert daily_consumption" ON public.daily_consumption;
CREATE POLICY "Allow anon and auth insert daily_consumption"
ON public.daily_consumption FOR INSERT
TO anon, authenticated
WITH CHECK (true);

DROP POLICY IF EXISTS "Allow anon and auth update daily_consumption" ON public.daily_consumption;
CREATE POLICY "Allow anon and auth update daily_consumption"
ON public.daily_consumption FOR UPDATE
TO anon, authenticated
USING (true)
WITH CHECK (true);

-- 6. SUPABASE REALTIME PUBLICATION (Requirement 20)
-- Enable Realtime events for energy_readings and daily_consumption
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' 
          AND schemaname = 'public' 
          AND tablename = 'energy_readings'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.energy_readings;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' 
          AND schemaname = 'public' 
          AND tablename = 'daily_consumption'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.daily_consumption;
    END IF;
END $$;

-- 7. SQL QUERY HELPER FUNCTIONS (Requirement 8)

-- RPC: Get persistent billing overview for a device
CREATE OR REPLACE FUNCTION public.get_billing_summary(
    p_device_code TEXT,
    p_rate NUMERIC DEFAULT 8.00
)
RETURNS TABLE (
    today_energy NUMERIC,
    today_cost NUMERIC,
    weekly_energy NUMERIC,
    weekly_cost NUMERIC,
    monthly_energy NUMERIC,
    monthly_cost NUMERIC,
    all_time_energy NUMERIC,
    all_time_cost NUMERIC
) AS $$
DECLARE
    v_today DATE := (NOW() AT TIME ZONE 'Asia/Kolkata')::DATE;
    v_week_start DATE := date_trunc('week', NOW() AT TIME ZONE 'Asia/Kolkata')::DATE;
    v_month_start DATE := date_trunc('month', NOW() AT TIME ZONE 'Asia/Kolkata')::DATE;
BEGIN
    RETURN QUERY
    WITH device_appliances AS (
        SELECT a.id 
        FROM public.appliances a
        JOIN public.devices d ON a.device_id = d.id
        WHERE d.device_code = p_device_code
    ),
    stats AS (
        SELECT
            COALESCE(SUM(CASE WHEN dc.consumption_date = v_today THEN dc.energy_kwh ELSE 0 END), 0) AS t_energy,
            COALESCE(SUM(CASE WHEN dc.consumption_date >= v_week_start AND dc.consumption_date <= v_today THEN dc.energy_kwh ELSE 0 END), 0) AS w_energy,
            COALESCE(SUM(CASE WHEN dc.consumption_date >= v_month_start AND dc.consumption_date <= v_today THEN dc.energy_kwh ELSE 0 END), 0) AS m_energy,
            COALESCE(SUM(dc.energy_kwh), 0) AS at_energy
        FROM public.daily_consumption dc
        WHERE dc.appliance_id IN (SELECT id FROM device_appliances)
    )
    SELECT 
        ROUND(t_energy, 4) AS today_energy,
        ROUND(t_energy * p_rate, 2) AS today_cost,
        ROUND(w_energy, 4) AS weekly_energy,
        ROUND(w_energy * p_rate, 2) AS weekly_cost,
        ROUND(m_energy, 4) AS monthly_energy,
        ROUND(m_energy * p_rate, 2) AS monthly_cost,
        ROUND(at_energy, 4) AS all_time_energy,
        ROUND(at_energy * p_rate, 2) AS all_time_cost
    FROM stats;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
