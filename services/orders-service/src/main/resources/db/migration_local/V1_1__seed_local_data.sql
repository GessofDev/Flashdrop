-- Seed data for H2 Local Development

INSERT INTO public.users (id,email,rut,name,last_name,phone,photo) VALUES
  (1,'cliente@demo.cl','11.111.111-1','Cliente','Demo','+56911111111',null),
  (2,'restaurante@demo.cl','22.222.222-2','Restaurante','Demo','+56922222222',null),
  (3,'repartidor@demo.cl','33.333.333-3','Repartidor','Demo','+56933333333',null),
  (4,'admin@demo.cl','44.444.444-4','Admin','Multirol','+56944444444',null),
  (5,'araucomaipu@flashdrop.cl','55.555.555-5','Flash Bites','Arauco Maipu','+56955555555',null);

INSERT INTO public.login (id,login,password,id_users,status) VALUES
  (1,'cliente@demo.cl','$2b$10$K0ZXwndhzN0H5uw6bfxF9eMqGEbiHunGMkv6/U9bE.BGr91d6tYM.',1,1),
  (2,'restaurante@demo.cl','$2b$10$K0ZXwndhzN0H5uw6bfxF9eMqGEbiHunGMkv6/U9bE.BGr91d6tYM.',2,1),
  (3,'repartidor@demo.cl','$2b$10$K0ZXwndhzN0H5uw6bfxF9eMqGEbiHunGMkv6/U9bE.BGr91d6tYM.',3,1),
  (4,'admin@demo.cl','$2b$10$K0ZXwndhzN0H5uw6bfxF9eMqGEbiHunGMkv6/U9bE.BGr91d6tYM.',4,1),
  (5,'araucomaipu@flashdrop.cl','$2b$10$K0ZXwndhzN0H5uw6bfxF9eMqGEbiHunGMkv6/U9bE.BGr91d6tYM.',5,1);

INSERT INTO public.roles (id,name,image,route) VALUES
  (1,'Cliente','','/client/products/list'),
  (2,'Restaurante','','/restaurant/orders/list'),
  (3,'Repartidor','','/delivery/orders/list');

INSERT INTO public.user_has_roles (id,id_user,id_rol) VALUES
  (1,1,1),(2,2,2),(3,3,3),(4,4,1),(5,4,2),(6,4,3),(7,5,2);

INSERT INTO public.client (id,user_id) VALUES (1,1),(2,4);

INSERT INTO public.restaurant (id,user_id,name,address) VALUES
  (1,2,'Urban Burger Demo','Av. Providencia 1200'),
  (2,4,'Flash Restaurant Demo','Los Leones 850'),
  (3,5,'Flash Bites Arauco Maipu','Av. Americo Vespucio 399, Maipu');

INSERT INTO public.delivery (id,user_id,vehicle) VALUES (1,3,'Moto'),(2,4,'Auto');

INSERT INTO public.categories (id,name,description,image) VALUES
  (1,'Hamburguesas','Combos y sandwiches preparados al momento','assets/img/burger1.png'),
  (2,'Pizzas','Pizzas familiares, medianas y porcionadas','assets/img/pizza.png'),
  (3,'Bebidas','Bebidas, jugos y aguas','assets/img/bag.png'),
  (4,'Promociones','Ofertas destacadas para delivery express','assets/img/hamburguesa.png');

INSERT INTO public.products (id,category_id,restaurant_id,name,description,price,image,is_available) VALUES
  (1,1,1,'Burger doble','Doble carne, cheddar, pepinillos y salsa de la casa',8990,'assets/img/burger1.png',true),
  (2,1,1,'Burger crispy','Pollo crispy, lechuga, tomate y mayo ahumada',7490,'assets/img/burger2.png',true),
  (3,4,1,'Combo urbano','Burger doble, papas fritas y bebida lata',11990,'assets/img/hamburguesa.png',true),
  (4,2,2,'Pizza pepperoni','Pizza familiar con pepperoni y extra queso',11500,'assets/img/pizza.png',true),
  (5,2,2,'Pizza vegetariana','Champinones, aceitunas, pimenton y mozzarella',10900,'assets/img/pizza2.png',true),
  (6,3,2,'Bebida lata','Bebida individual 350cc',1490,'assets/img/bag.png',true),
  (7,4,2,'Pack familiar pizza','Pizza familiar, 2 bebidas y pan de ajo',15990,'assets/img/pizza.png',true),
  (8,1,1,'Cuarto de libra','queso y carne',9000,'https://img.asmedia.epimg.net/resizer/v2/FQLWD65HWNDKPHTIUK3IBKHFOM.png?auth=ad3df6cdb1bbf8b840447c8b2e4142364feed32d475a9e18cb91d0c11ba47a10&width=375',true),
  (9,3,2,'MCnico','asdasd',10000,null,true),
  (10,1,3,'Burger Arauco','Carne, queso cheddar, tomate y salsa de la casa',8990,'assets/img/burger1.png',true),
  (11,2,3,'Pizza Maipu familiar','Pepperoni, mozzarella y salsa de tomate',12990,'assets/img/pizza.png',true),
  (12,4,3,'Papas cheddar','Papas crujientes con cheddar y cebollin',4990,'assets/img/bag.png',true),
  (13,3,3,'Bebida 500 cc','Bebida individual fria',1990,'assets/img/bag.png',true);

INSERT INTO public.orders (id,client_id,restaurant_id,delivery_id,status,address,subtotal,delivery_fee,total,payment_method) VALUES
  (1,1,1,1,'Preparando','Av. Providencia 1200, Santiago',19480,2500,21980,'Tarjeta'),
  (2,1,2,1,'Listo para retiro','Los Leones 850, Santiago',11500,2200,13700,'Efectivo'),
  (3,2,1,2,'En camino','Nueva Costanera 3900, Vitacura',11990,3000,14990,'Tarjeta'),
  (4,2,2,null,'Nuevo pedido','Manuel Montt 420, Providencia',17480,2500,19980,'Transferencia'),
  (5,1,1,1,'Entregado','Santa Isabel 060, Santiago',8990,2000,10990,'Tarjeta'),
  (6,1,2,1,'Nuevo pedido','Av. Providencia 1200, Santiago',15990,2500,18490,'Efectivo'),
  (7,1,1,1,'Nuevo pedido','Juan bohon 1622, Santiago',7490,2500,9990,'Efectivo'),
  (8,1,2,1,'Nuevo pedido','selene 1421, Santiago',10900,2500,13400,'Efectivo'),
  (9,1,1,1,'Nuevo pedido','selene 1422, Santiago',11990,2500,14490,'Efectivo'),
  (10,1,2,1,'Nuevo pedido','Av. Providencia 125, Santiago',10000,2500,12500,'Efectivo');

INSERT INTO public.order_items (id,order_id,product_id,quantity,unit_price,total) VALUES
  (1,1,1,2,8990,17980),(2,1,6,1,1490,1490),(3,2,4,1,11500,11500),
  (4,3,3,1,11990,11990),(5,4,5,1,10900,10900),(6,4,6,2,1490,2980),
  (7,5,1,1,8990,8990),(8,6,7,1,15990,15990),(9,7,2,1,7490,7490),
  (10,8,5,1,10900,10900),(11,9,3,1,11990,11990),(12,10,9,1,10000,10000);

INSERT INTO public.delivery_routes (id,order_id,pickup_address,delivery_address,distance_km,estimated_minutes,status) VALUES
  (1,1,'Urban Burger Demo, Av. Providencia 1000','Av. Providencia 1200, Santiago',2.4,18,'Retirar pedido'),
  (2,2,'Flash Restaurant Demo, Los Leones 500','Los Leones 850, Santiago',4.1,25,'Listo para retiro'),
  (3,3,'Urban Burger Demo, Av. Providencia 1000','Nueva Costanera 3900, Vitacura',6.8,32,'En camino'),
  (4,5,'Urban Burger Demo, Av. Providencia 1000','Santa Isabel 060, Santiago',1.8,12,'Entregado'),
  (5,6,'Los Leones 850','Av. Providencia 1200, Santiago',3.2,20,'Pendiente'),
  (6,7,'Av. Providencia 1200','Juan bohon 1622, Santiago',3.2,20,'Pendiente'),
  (7,8,'Los Leones 850','selene 1421, Santiago',3.2,20,'Pendiente'),
  (8,9,'Av. Providencia 1200','selene 1422, Santiago',3.2,20,'Pendiente'),
  (9,10,'Los Leones 850','Av. Providencia 125, Santiago',3.2,20,'Pendiente');

-- Reset H2 IDENTITY sequences so new inserts don't conflict with seeded data
ALTER TABLE public.users ALTER COLUMN id RESTART WITH 6;
ALTER TABLE public.login ALTER COLUMN id RESTART WITH 6;
ALTER TABLE public.roles ALTER COLUMN id RESTART WITH 4;
ALTER TABLE public.user_has_roles ALTER COLUMN id RESTART WITH 8;
ALTER TABLE public.client ALTER COLUMN id RESTART WITH 3;
ALTER TABLE public.restaurant ALTER COLUMN id RESTART WITH 4;
ALTER TABLE public.delivery ALTER COLUMN id RESTART WITH 3;
ALTER TABLE public.categories ALTER COLUMN id RESTART WITH 5;
ALTER TABLE public.products ALTER COLUMN id RESTART WITH 14;
ALTER TABLE public.orders ALTER COLUMN id RESTART WITH 11;
ALTER TABLE public.order_items ALTER COLUMN id RESTART WITH 13;
ALTER TABLE public.delivery_routes ALTER COLUMN id RESTART WITH 10;

