import sys
import re

with open('windows-app/CMakeLists.txt', 'r') as f:
    content = f.read()

# Make sure _ENFORCE_FACET_SPECIALIZATIONS is defined
if '_ENFORCE_FACET_SPECIALIZATIONS' not in content:
    content = content.replace('add_compile_definitions(_HAS_STD_BYTE=0)', 'add_compile_definitions(_HAS_STD_BYTE=0 _ENFORCE_FACET_SPECIALIZATIONS=1)')

with open('windows-app/CMakeLists.txt', 'w') as f:
    f.write(content)
