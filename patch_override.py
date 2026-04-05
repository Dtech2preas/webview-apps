with open('app/src/main/java/com/dtech/automation/MainActivity.java', 'r') as f:
    lines = f.readlines()

new_lines = []
skip = False
for i, line in enumerate(lines):
    if line.strip() == '@Override' and lines[i+1].strip() == '' and lines[i+2].strip() == '@Override':
        pass # Skip the first @Override
    else:
        new_lines.append(line)

with open('app/src/main/java/com/dtech/automation/MainActivity.java', 'w') as f:
    f.writelines(new_lines)
