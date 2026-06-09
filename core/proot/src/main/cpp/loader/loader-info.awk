# Note: This file is included only for targets which have pokedata workaround
# It extracts addresses of 'pokedata_workaround' and '_start' from readelf output.
# We let the C compiler handle the hex math to avoid gawk dependencies.

BEGIN {
    print "#include <unistd.h>"
    print "#include \"arch.h\""
    print "#ifdef HAS_POKEDATA_WORKAROUND"
}

$NF == "pokedata_workaround" { pokedata_workaround = "0x" $2 }
$NF == "_start" { start = "0x" $2 }

END {
    if (pokedata_workaround == "") pokedata_workaround = "0"
    if (start == "") start = "0"
    print "const ssize_t offset_to_pokedata_workaround = (ssize_t)(" pokedata_workaround " - " start ");"
    print "#endif"
}
