(apk info -e python3 || apk add python3) &&
(apk info -e py3-pip || apk add py3-pip) &&
python3 $@