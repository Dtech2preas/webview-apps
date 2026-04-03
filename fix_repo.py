import re

file_path = "app/src/main/java/com/dtech/automation/ServiceRepository.java"
with open(file_path, "r") as f:
    content = f.read()

# Fix the multi-line string issue in Java
content = content.replace('sb.append(line).append("\n");', 'sb.append(line).append("\\n");')

with open(file_path, "w") as f:
    f.write(content)
