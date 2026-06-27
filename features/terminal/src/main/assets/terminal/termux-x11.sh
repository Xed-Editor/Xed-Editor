[ -z "${XKB_CONFIG_ROOT+x}" ] && export XKB_CONFIG_ROOT=/usr/share/X11/xkb
export XSTARTUP_CLASSPATH="$TERMUX_X11_SOURCE_DIR"
app_process -classpath "$TERMUX_X11_SOURCE_DIR" -Xnoimage-dex2oat / --nice-name="termux-x11 com.termux.x11 $*" com.termux.x11.CmdEntryPoint "$@"