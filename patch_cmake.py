import sys
import re

with open('windows-app/CMakeLists.txt', 'r') as f:
    content = f.read()

# Add compile definitions to fix stdext undeclared identifier in Qt 6.5.0 with MSVC
if 'add_compile_definitions' not in content:
    content = re.sub(
        r'(set\(CMAKE_AUTOUIC ON\))',
        r'\1\n\n# Fix MSVC C2065 error by disabling permissive mode and defining _HAS_STD_BYTE=0\nadd_compile_options(/permissive-)\nadd_compile_definitions(_HAS_STD_BYTE=0)',
        content
    )

with open('windows-app/CMakeLists.txt', 'w') as f:
    f.write(content)
