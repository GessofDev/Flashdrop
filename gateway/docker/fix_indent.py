import sys

compose_path = r"D:\desarrollo\2027\flashdrop_backend\gateway\docker\docker-compose.stack.yml"

with open(compose_path) as f:
    content = f.read()

lines = content.split("\n")
out = []
mode = None  # None, "priv", "pub"

for ln in lines:
    s = ln.lstrip()
    sp = ln[:len(ln)-len(s)]

    if "JWT_PRIVATE_KEY: |" in ln:
        mode = "priv"
        out.append(ln)
    elif "JWT_PUBLIC_KEY: |" in ln:
        mode = "pub"
        out.append(ln)
    elif ln.strip() == "" or (s.startswith("- ") and not s.startswith("---")):
        # Empty line, OR new YAML list item (- followed by space, not triple-dash)
        mode = None
        out.append(ln)
    else:
        if mode == "priv" or mode == "pub":
            # Inside a block-scalar content — must be MORE indented than parent
            # Parent indent is 6 spaces, content must be 8+
            # Re-indent by adding 2 spaces
            out.append("  " + ln)
        else:
            out.append(ln)

new_content = "\n".join(out)
with open(compose_path, "w") as f:
    f.write(new_content)
print("Re-indentation applied")
