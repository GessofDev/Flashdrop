import sys

compose_path = r"D:\desarrollo\2027\flashdrop_backend\gateway\docker\docker-compose.stack.yml"

with open(compose_path) as f:
    content = f.read()

# Remove the duplicate catalog-service block (lines 64-66)
content = content.replace(
    '\n    networks: [stack]\n    expose: ["8082"]\n    healthcheck:\n      test:',
    '\n    healthcheck:\n      test:',
    1,
)

with open(compose_path, "w") as f:
    f.write(content)
print("cleaned")
