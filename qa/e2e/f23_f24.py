"""F23 (eliminar cuenta) y F24 (alta de tienda y repartidor) de punta a punta, a traves del gateway.

Usa cuentas que crea la propia prueba (nunca las cuentas demo). Ver qa/e2e/README.md para levantar el entorno.
"""
import json
import os
import subprocess
import sys
import time
import urllib.request
import urllib.error

GW = os.environ.get("FD_GATEWAY", "http://127.0.0.1:13000")
AUTH_DIRECT = os.environ.get("FD_AUTH_DIRECT", "http://127.0.0.1:18081")
KEY = os.environ.get("FD_INTERNAL_KEY", "fdtest-key")
PW = "Segura1234"
STAMP = str(int(time.time()))
results = []


def call(method, url, body=None, token=None, headers=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            raw = r.read().decode()
            return r.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, raw


def check(name, cond, detail=""):
    results.append((name, bool(cond)))
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else "  -> " + str(detail)))
    return cond


def psql(container, db, sql):
    out = subprocess.run(["docker", "exec", container, "psql", "-U", "postgres", "-d", db, "-t", "-A", "-c", sql],
                         capture_output=True, text=True)
    return out.stdout.strip()


def register(label):
    email = f"qa-{label}-{STAMP}@flashdrop.cl"
    s, b = call("POST", GW + "/auth/register", {"email": email, "password": PW, "name": label, "lastName": "QA"})
    assert s == 201, (s, b)
    uid = int(psql("fdtest-auth", "flashdrop_auth", f"select id from users where email='{email}'"))
    return email, uid


def login(email):
    s, b = call("POST", GW + "/auth/login", {"login": email, "password": PW})
    assert s == 200, (s, b)
    return b["accessToken"]


def assign_internal(uid, roles):
    s, b = call("PUT", f"{AUTH_DIRECT}/api/internal/users/{uid}/roles", {"roles": roles},
                headers={"X-Internal-Api-Key": KEY})
    assert s == 200, (s, b)


def wait_deleted(uid, seconds=40):
    end = time.time() + seconds
    while time.time() < end:
        if psql("fdtest-auth", "flashdrop_auth", f"select count(*) from users where id={uid}") == "0":
            return True
        time.sleep(2)
    return False


print("=== Preparacion: cuentas de prueba propias (nada de las cuentas demo) ===")
admin_email, admin_id = register("admin")
owner_email, owner_id = register("dueno")
courier_email, courier_id = register("repartidor")
client_email, client_id = register("cliente")
courier2_email, courier2_id = register("repartidor2")
assign_internal(admin_id, ["Cliente", "Restaurante", "Repartidor"])  # primer administrador: endpoint interno
admin = login(admin_email)

print("\n=== F24: el administrador da de alta una tienda y un repartidor (a traves del gateway) ===")
s, b = call("GET", GW + "/auth/admin/users?email=" + owner_email, token=login(client_email))
check("F24 un cliente NO puede buscar usuarios (403)", s == 403, (s, b))
s, b = call("GET", GW + "/auth/admin/users?email=" + owner_email)
check("F24 sin token no entra (401)", s == 401, (s, b))
s, b = call("GET", GW + "/auth/admin/users?email=" + owner_email, token=admin)
check("F24 el admin encuentra al futuro dueno con rol Cliente", s == 200 and b["id"] == owner_id and b["roles"] == ["Cliente"], (s, b))
s, b = call("PUT", f"{GW}/auth/admin/users/{owner_id}/roles", {"roles": ["Cliente", "Restaurante"]}, token=admin)
check("F24 el admin asigna Restaurante", s == 200 and len(b["roles"]) == 2, (s, b))
s, b = call("PUT", f"{GW}/auth/admin/users/{courier_id}/roles", {"roles": ["Cliente", "Repartidor"]}, token=admin)
check("F24 el admin asigna Repartidor", s == 200, (s, b))
s, b = call("PUT", f"{GW}/auth/admin/users/{courier_id}/roles", {"roles": ["Soporte"]}, token=admin)
check("F24 Soporte no se puede asignar por esta via (400)", s == 400, (s, b))
s, b = call("PUT", f"{GW}/auth/admin/users/{admin_id}/roles", {"roles": ["Cliente"]}, token=admin)
check("F24 el admin no puede cambiar sus propios roles (403)", s == 403, (s, b))

s, b = call("POST", GW + "/api/catalog/admin/restaurants", {"name": "Tienda QA " + STAMP, "address": "Calle QA 123", "ownerUserId": owner_id}, token=login(client_email))
check("F24 un cliente no puede crear tiendas (403)", s == 403, (s, b))
s, b = call("POST", GW + "/api/catalog/admin/restaurants", {"name": "Tienda QA " + STAMP, "address": "Calle QA 123", "ownerUserId": owner_id}, token=admin)
rest_id = b["id"] if s == 201 else None
check("F24 el admin crea la tienda: nace cerrada y con dueno", s == 201 and b["ownerUserId"] == owner_id and b["acceptingOrders"] is False, (s, b))
s, b = call("POST", GW + "/api/catalog/admin/restaurants", {"name": "Otra", "ownerUserId": owner_id}, token=admin)
check("F24 un dueno con tienda no puede tener otra (409)", s == 409, (s, b))

owner = login(owner_email)
s, b = call("GET", GW + "/api/catalog/my/restaurant", token=owner)
check("F24 el nuevo dueno ve su tienda", s == 200 and b["id"] == rest_id, (s, b))
s, b = call("PUT", GW + "/api/catalog/my/restaurant/open-status", {"acceptingOrders": True}, token=owner)
check("F24 el dueno abre la tienda", s == 200 and b["acceptingOrders"] is True, (s, b))
s, b = call("POST", GW + "/api/catalog/my/products", {"categoryId": 1, "name": "Burger QA", "description": "prueba", "price": 5000, "available": True}, token=owner)
prod_id = b["id"] if s == 201 else None
check("F24 el dueno crea un producto", s == 201, (s, b))
s, b = call("GET", GW + "/catalog/restaurants")
check("F24 la tienda aparece en el catalogo publico y abierta", s == 200 and any(r["id"] == rest_id and r["open"] for r in b), (s, str(b)[:200]))

s, b = call("POST", GW + "/api/delivery/admin/persons", {"userId": courier_id, "vehicle": "MOTO"}, token=admin)
check("F24 el admin crea el perfil de repartidor", s == 201 and b["data"]["userId"] == str(courier_id), (s, b))
s, b = call("POST", GW + "/api/delivery/admin/persons", {"userId": courier_id}, token=admin)
check("F24 el perfil duplicado responde 409", s == 409, (s, b))
courier = login(courier_email)
s, b = call("POST", GW + "/api/delivery/admin/persons", {"userId": courier2_id}, token=courier)
check("F24 un repartidor (no admin) no puede crear perfiles (403)", s == 403, (s, b))

client = login(client_email)
s, b = call("POST", GW + "/api/orders", {"address": "Av. Casa del Cliente 456", "paymentMethod": "Efectivo", "items": [{"productId": prod_id, "quantity": 1}]}, token=client)
order_id = None
if s == 201:
    order_id = b["data"]["id"]
check("F24 un usuario recien registrado compra por primera vez (se crea su perfil)", s == 201, (s, b))
oid = order_id
oid_num = int(oid.split("-")[-1], 16)  # la API usa UUID; la base y Delivery usan el numero

print("\n=== F23: eliminar la cuenta ===")
s, b = call("DELETE", GW + "/auth/account", {"password": PW}, token=client)
check("F23 con pedido en curso se rechaza (409) y no se cancela nada", s == 409 and b.get("code") == "ACCOUNT_HAS_ACTIVE_PROCESSES", (s, b))
st = psql("fdtest-orders", "flashdrop_orders", f"select status from orders where id={oid_num}") if oid else ""
check("F23 el pedido sigue en su estado (no se cancelo)", st == "Nuevo pedido", st)
s, b = call("GET", GW + "/api/orders", token=client)
check("F23 la cuenta sigue funcionando", s == 200, (s, b))
s, b = call("DELETE", GW + "/auth/account", {"password": PW}, token=owner)
check("F23 el dueno con pedidos pendientes tampoco puede eliminar (409)", s == 409, (s, b))

# el pedido recorre su flujo normal hasta Entregado
s, b = call("PUT", f"{GW}/api/orders/{oid}/status", {"status": "Preparando"}, token=owner)
check("flujo: tienda pasa a Preparando", s == 200, (s, b))
s, b = call("PUT", f"{GW}/api/orders/{oid}/status", {"status": "Listo para retiro"}, token=owner)
check("flujo: tienda pasa a Listo para retiro", s == 200, (s, b))
s, b = call("GET", f"{GW}/api/orders/available-for-delivery?restaurant_id={rest_id}", token=courier)
check("F24 el repartidor nuevo ve el pedido disponible", s == 200 and any(str(x.get('id')) == str(oid) or x.get('code') for x in (b.get("data") or [])), (s, str(b)[:300]))
s, b = call("POST", GW + "/api/delivery/claim", {"orderIds": [oid_num]}, token=courier)
check("flujo: el repartidor toma el pedido", s in (200, 201), (s, b))
s, b = call("DELETE", GW + "/auth/account", {"password": PW}, token=courier)
check("F23 el repartidor con un pedido asignado no puede eliminar (409)", s == 409, (s, b))
s, b = call("PUT", f"{GW}/api/orders/{oid}/status", {"status": "Retirado"}, token=courier)
check("flujo: Retirado", s == 200, (s, b))
s, b = call("GET", f"{GW}/api/orders/{oid}", token=client)
code = (b.get("data") or {}).get("deliveryCode") if s == 200 else None
check("flujo: el cliente ve su codigo de entrega", bool(code), (s, str(b)[:200]))
s, b = call("POST", f"{GW}/api/orders/{oid}/deliver", {"code": code}, token=courier)
check("flujo: Entregado con el codigo", s == 200, (s, b))

s, b = call("DELETE", GW + "/auth/account", {"password": "otra-clave"}, token=client)
check("F23 con contrasena incorrecta se rechaza (401)", s == 401, (s, b))
s, b = call("DELETE", GW + "/auth/account", {"password": PW})
check("F23 sin token se rechaza (401)", s == 401, (s, b))
client_pre_user = psql("fdtest-orders", "flashdrop_orders", f"select user_id from client where user_id={client_id}")
check("F23 antes de eliminar, Orders conoce al cliente", client_pre_user == str(client_id), client_pre_user)
s, b = call("DELETE", GW + "/auth/account", {"password": PW}, token=client)
check("F23 sin pedidos en curso se acepta (202)", s == 202, (s, b))
s, b = call("POST", GW + "/auth/login", {"login": client_email, "password": PW})
check("F23 la cuenta ya no puede iniciar sesion", s == 401, (s, b))
s, b = call("GET", GW + "/api/orders", token=client)
check("F23 el token anterior deja de valer al instante en Orders", s == 401, (s, str(b)[:200]))
check("F23 el usuario desaparece de Auth", wait_deleted(client_id))
check("F23 no queda solicitud pendiente", psql("fdtest-auth", "flashdrop_auth", f"select count(*) from account_deletions where user_id={client_id}") == "0")
check("F23 Orders: el pedido se conserva (estado y monto) sin direccion",
      psql("fdtest-orders", "flashdrop_orders", f"select status||'|'||total||'|'||coalesce(address,'NULL') from orders where id={oid_num}") == "Entregado|7500.00|",
      psql("fdtest-orders", "flashdrop_orders", f"select status||'|'||total||'|'||coalesce(address,'NULL') from orders where id={oid_num}"))
check("F23 Orders: el perfil de cliente ya no tiene vinculo con el usuario",
      psql("fdtest-orders", "flashdrop_orders", f"select count(*) from client where user_id={client_id}") == "0"
      and psql("fdtest-orders", "flashdrop_orders", "select count(*) from client where user_id is null and end_date is not null") != "0")
check("F23 Delivery: la direccion de entrega de la ruta quedo vacia",
      psql("fdtest-delivery", "delivery_db", f"select coalesce(delivery_address,'NULL') from internal.delivery_routes where order_id={oid_num}") == "")
s, b = call("GET", GW + "/api/orders", token=owner)
check("F23 la tienda ve el pedido sin nombre ni direccion del cliente",
      s == 200 and any(str(x.get("id")) == str(oid) and x.get("clientName") == "" and x.get("address") == "" for x in b["data"]), (s, str(b)[:400]))

s, b = call("DELETE", GW + "/auth/account", {"password": PW}, token=courier)
check("F23 el repartidor sin pedidos en curso puede eliminar (202)", s == 202, (s, b))
check("F23 el repartidor desaparece de Auth", wait_deleted(courier_id))
check("F23 Delivery: su perfil de repartidor se borra",
      psql("fdtest-delivery", "delivery_db", f"select count(*) from internal.delivery_persons where user_id='{courier_id}'") == "0")
check("F23 Delivery: sus rutas ya no apuntan a el",
      psql("fdtest-delivery", "delivery_db", f"select count(*) from internal.delivery_routes where order_id={oid_num} and delivery_person_id is not null") == "0")

s, b = call("DELETE", GW + "/auth/account", {"password": PW}, token=owner)
check("F23 el dueno sin pedidos en curso puede eliminar (202)", s == 202, (s, b))
check("F23 el dueno desaparece de Auth", wait_deleted(owner_id))
row = psql("fdtest-catalog", "flashdrop_catalog", f"select coalesce(user_id::text,'NULL')||'|'||accepting_orders||'|'||name from restaurant where id={rest_id}")
check("F23 Catalog: la tienda se conserva, sin dueno y cerrada", row.startswith("NULL|false|Tienda QA"), row)
check("F23 Catalog: sus productos se conservan", psql("fdtest-catalog", "flashdrop_catalog", f"select count(*) from products where restaurant_id={rest_id}") == "1")

print("\n=== Resultado ===")
failed = [n for n, ok in results if not ok]
print(f"{len(results) - len(failed)} de {len(results)} comprobaciones correctas")
for n in failed:
    print("FALLO:", n)
sys.exit(1 if failed else 0)
