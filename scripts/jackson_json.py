"""Serialize JSON in the exact DefaultPrettyPrinter shape Jackson writes.

Keeps hand-edited data-file diffs minimal: objects indent one level per field,
arrays stay inline ("[ a, b ]"), empty containers keep their inner spaces, and
files end without a trailing newline.
"""


def dumps(value):
    return _write(value, 0)


def _write(value, indent):
    if value is None:
        return "null"
    if value is True:
        return "true"
    if value is False:
        return "false"
    if isinstance(value, str):
        import json
        return json.dumps(value, ensure_ascii=False)
    if isinstance(value, (int, float)):
        import json
        return json.dumps(value)
    pad = " " * indent
    inner = " " * (indent + 2)
    if isinstance(value, list):
        if not value:
            return "[ ]"
        parts = [_write(item, indent) for item in value]
        return "[" + " " + ", ".join(parts) + " ]"
    if isinstance(value, dict):
        if not value:
            return "{ }"
        fields = [
            inner + _write(str(key), indent) + " : " + _write(item, indent + 2)
            for key, item in value.items()
        ]
        return "{\n" + ",\n".join(fields) + "\n" + pad + "}"
    raise TypeError(type(value))
