insert into public.categories (id, name, description, image)
values
    (1, 'Hamburguesas', 'Combos y sandwiches preparados al momento', 'assets/img/burger1.png'),
    (2, 'Pizzas', 'Pizzas familiares, medianas y porcionadas', 'assets/img/pizza.png'),
    (3, 'Bebidas', 'Bebidas, jugos y aguas', 'assets/img/bag.png'),
    (4, 'Promociones', 'Ofertas destacadas para delivery express', 'assets/img/hamburguesa.png')
on conflict (id) do nothing;

insert into public.restaurant (id, user_id, name, address)
values
    (1, 2, 'Urban Burger Demo', 'Av. Providencia 1200, Santiago'),
    (2, 4, 'Flash Restaurant Demo', 'Los Leones 850, Santiago'),
    (3, 5, 'Flash Bites Arauco Maipu', 'Av. Americo Vespucio 399, Maipu')
on conflict (id) do nothing;

insert into public.products (id, category_id, restaurant_id, name, description, price, image, is_available)
values
    (1, 1, 1, 'Burger doble', 'Doble carne, queso y salsa de la casa', 8990, 'assets/img/burger1.png', true),
    (2, 1, 1, 'Papas cheddar', 'Papas crujientes con cheddar y cebollin', 4990, 'assets/img/bag.png', true),
    (3, 2, 3, 'Pizza Maipu familiar', 'Pepperoni, mozzarella y salsa de tomate', 12990, 'assets/img/pizza.png', true),
    (4, 3, 3, 'Bebida 500 cc', 'Bebida individual fria', 1990, 'assets/img/bag.png', true),
    (5, 4, 2, 'Combo Flash', 'Burger, papas y bebida para delivery express', 10990, 'assets/img/hamburguesa.png', true),
    (6, 1, 3, 'Burger Maipu', 'Hamburguesa clasica con queso y salsa flash', 7990, 'assets/img/burger1.png', true)
on conflict (id) do nothing;

select setval(pg_get_serial_sequence('public.categories', 'id'), coalesce((select max(id) from public.categories), 1));
select setval(pg_get_serial_sequence('public.restaurant', 'id'), coalesce((select max(id) from public.restaurant), 1));
select setval(pg_get_serial_sequence('public.products', 'id'), coalesce((select max(id) from public.products), 1));