insert into public.categories (id, name, description, image)
values
  (1, 'Hamburguesas', 'Combos y sandwiches preparados al momento', 'assets/img/burger1.png'),
  (2, 'Pizzas', 'Pizzas familiares, medianas y porcionadas', 'assets/img/pizza.png'),
  (3, 'Bebidas', 'Bebidas, jugos y aguas', 'assets/img/bag.png'),
  (4, 'Promociones', 'Ofertas destacadas para delivery express', 'assets/img/hamburguesa.png')
on conflict (id) do update set
  name = excluded.name,
  description = excluded.description,
  image = excluded.image;

insert into public.restaurant (id, user_id, name, address)
values
  (1, 2, 'Urban Burger Demo', 'Av. Providencia 1200, Santiago'),
  (2, 4, 'Flash Restaurant Demo', 'Los Leones 850, Santiago'),
  (3, 5, 'Flash Bites Arauco Maipu', 'Av. Americo Vespucio 399, Maipu')
on conflict (id) do update set
  user_id = excluded.user_id,
  name = excluded.name,
  address = excluded.address;

insert into public.products (id, category_id, restaurant_id, name, description, price, image, is_available)
values
  (1, 1, 1, 'Burger doble', 'Doble carne, queso y salsa de la casa', 8990, 'assets/img/burger1.png', true),
  (2, 1, 1, 'Papas cheddar', 'Papas crujientes con cheddar y cebollin', 4990, 'assets/img/bag.png', true),
  (3, 2, 3, 'Pizza Maipu familiar', 'Pepperoni, mozzarella y salsa de tomate', 12990, 'assets/img/pizza.png', true),
  (4, 3, 3, 'Bebida 500 cc', 'Bebida individual fria', 1990, 'assets/img/bag.png', true)
on conflict (id) do update set
  category_id = excluded.category_id,
  restaurant_id = excluded.restaurant_id,
  name = excluded.name,
  description = excluded.description,
  price = excluded.price,
  image = excluded.image,
  is_available = excluded.is_available;

select setval(pg_get_serial_sequence('public.categories', 'id'), (select max(id) from public.categories));
select setval(pg_get_serial_sequence('public.restaurant', 'id'), (select max(id) from public.restaurant));
select setval(pg_get_serial_sequence('public.products', 'id'), (select max(id) from public.products));
