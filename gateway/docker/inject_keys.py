import sys

compose_path = r"D:\desarrollo\2027\flashdrop_backend\gateway\docker\docker-compose.stack.yml"
env_path = r"D:\desarrollo\2027\flashdrop_backend\gateway\docker\secrets\jwt.env"

# Read keys and concatenate to single-line (no newlines, decoder strips whitespace anyway)
with open(r"D:\desarrollo\2027\flashdrop_backend\gateway\docker\secrets\jwt-private.pem") as f:
    priv = "".join(line.strip() for line in f if line.strip())
with open(r"D:\desarrollo\2027\flashdrop_backend\gateway\docker\secrets\jwt-public.pem") as f:
    pub = "".join(line.strip() for line in f if line.strip())

print(f"Private: {priv[:50]}...  len={len(priv)}")
print(f"Public:  {pub[:50]}... len={len(pub)}")

# Write env_file with single-line keys
with open(env_path, "w") as f:
    f.write(f"JWT_PRIVATE_KEY={priv}\n")
    f.write(f"JWT_PUBLIC_KEY={pub}\n")
print(f"env_file written: {env_path}")

# Edit docker-compose.stack.yml to remove broken block and add env_file
with open(compose_path) as f:
    content = f.read()

# Find and remove the entire broken block (everything from
# "      - JWT_PRIVATE_KEY: |" up to "      -----END PUBLIC KEY-----" inclusive)
import re
new_content = re.sub(
    r"      - JWT_PRIVATE_KEY: \|\n(?:.*\n)*?      -----END PUBLIC KEY-----\n",
    "",
    content,
    count=1,
)
if new_content == content:
    print("WARN: regex did not match the broken block")
else:
    print("Broken block removed from compose file")

# Remove the JWT_ALLOW_EPHEMERAL line (no longer needed)
new_content = new_content.replace(
    "      - JWT_ALLOW_EPHEMERAL=true\n", ""
)

# Add env_file directive to auth-service
# We need to insert env_file under auth-service service, BEFORE networks
auth_block_start = new_content.find("  auth-service:\n")
auth_block_end = new_content.find("\n    networks: [stack]\n", auth_block_start)
insertion = "    env_file:\n      - ./secrets/jwt.env\n"
new_content = (
    new_content[:auth_block_end] + insertion + new_content[auth_block_end:]
)
print("env_file directive inserted under auth-service")

with open(compose_path, "w") as f:
    f.write(new_content)
print("compose file updated")
