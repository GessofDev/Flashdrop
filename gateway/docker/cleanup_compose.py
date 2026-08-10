import sys

compose_path = r"D:\desarrollo\2027\flashdrop_backend\gateway\docker\docker-compose.stack.yml"

with open(compose_path) as f:
    content = f.read()

# Fix catalog-service line: split "ROLE_KEY=...}    env_file:" onto proper lines
content = content.replace(
    "      - SUPABASE_SERVICE_ROLE_KEY=${SUPABASE_SERVICE_ROLE_KEY}    env_file:\n      - ./secrets/jwt.env\n",
    "      - SUPABASE_SERVICE_ROLE_KEY=${SUPABASE_SERVICE_ROLE_KEY}\n\n    networks: [stack]\n    expose: [\"8082\"]\n",
    1,
)

with open(compose_path, "w") as f:
    f.write(content)
print("cleaned catalog-service block")
