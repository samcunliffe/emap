-- Add mrn

INSERT INTO public.mrn (mrn_id, mrn, nhs_number, source_system, stored_from) VALUES
    (1001, '40800000', '9999999999', 'EPIC', '2020-09-01 11:04:04.794000'),
    (1002, '60600000', '1111111111', 'caboodle', '2020-09-03 10:05:04.794000'),
    (1003, '30700000', null, 'EPIC', '2020-09-10 16:01:05.371000'),
    (1004, '60900000', '2222222222', 'another', '2020-09-10 17:02:08.000000');

-- Add mrn_to_live

INSERT INTO public.mrn_to_live (mrn_to_live_id, stored_from, stored_until, valid_from, valid_until, live_mrn_id, mrn_id) VALUES
    (1001, '2020-09-01 11:04:04.794000', null, '2020-09-01 11:04:04.794000', null, 1001, 1001),
    (1002, '2020-09-03 10:05:04.794000', null, '2020-09-03 11:04:04.794000', null, 1003, 1002),
    (1003, '2020-09-10 16:01:05.371000', null, '2020-09-03 13:06:05.794000', null, 1003, 1003),
    (1004, '2020-09-10 17:02:08.000000', null, '2020-09-05 14:05:04.794000', null, 1004, 1004);