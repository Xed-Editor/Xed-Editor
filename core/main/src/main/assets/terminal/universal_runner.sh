set -e
file="$1"

if [ ! -f "$file" ]; then
  echo "Error: File not found -> $file"
  exit 1
fi

source "$LOCAL/bin/utils"

run_code() {
    echo -e "\e[32;1m[✓]\e[37m Compilation successful! Running...\e[0m"
    chmod +x "$1"
    if [ -x "$1" ]; then
        "$1"
        rm "$1"
    else
        mv "$1" /tmp/a.out
        chmod +x /tmp/a.out
        /tmp/a.out
        rm /tmp/a.out
    fi
}

install_package() {
  local packages="$1"
  info "Installing $packages..."
  apt update -y && apt upgrade -y
  apt install -y $packages
}

install_nodejs() {
  echo "Installing Node.js LTS..."
  install_package "curl"
  curl -fsSL https://deb.nodesource.com/setup_lts.x | bash -
  apt install -y nodejs
}

install_rust() {
  echo "Installing Rust..."
  install_package "curl"
  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
  source "$HOME/.cargo/env"
  # Add to PATH for current session
  export PATH="$HOME/.cargo/bin:$PATH"
}

install_dotnet() {
  echo "Installing .NET SDK..."
  wget https://packages.microsoft.com/config/ubuntu/22.04/packages-microsoft-prod.deb -O packages-microsoft-prod.deb
  dpkg -i packages-microsoft-prod.deb
  rm packages-microsoft-prod.deb
  apt update -qq
  apt install -y dotnet-sdk-8.0
}

install_kotlin() {
    install_package "unzip curl"
    echo "Fetching latest Kotlin compiler..."
    url=$(curl -s https://api.github.com/repos/JetBrains/kotlin/releases/latest \
        | grep "browser_download_url" \
        | grep "kotlin-compiler-.*zip" \
        | grep -v ".sha256" \
        | cut -d '"' -f 4)

    if [ -z "$url" ]; then
        echo "Error: Could not find Kotlin compiler download URL."
        return 1
    fi

    echo "Downloading from: $url"
    curl -L -o /tmp/kotlin.zip "$url"

    echo "Extracting to /opt/kotlinc ..."
    mkdir -p /opt/kotlinc
    unzip -qo /tmp/kotlin.zip -d /opt

    ln -sf /opt/kotlinc/bin/kotlinc /usr/local/bin/kotlinc
    ln -sf /opt/kotlinc/bin/kotlin /usr/local/bin/kotlin

    echo "Kotlin installed at /opt/kotlinc"
}

case "$file" in
  *.py)
    if ! command_exists python3; then
      install_package "python3 python3-pip python3-venv"
    fi
    python3 "$file"
    ;;

  *.js)
    if ! command_exists node; then
      install_nodejs
    fi
    node "$file"
    ;;

  *.ts)
    if ! command_exists node; then
      install_nodejs
    fi
    if ! command_exists tsc; then
      echo "Installing TypeScript compiler..."
      npm install -g typescript
    fi
    tsc "$file" --outDir ./temp && node "./temp/$(basename "$file" .ts).js"
    rm -rf ./temp
    ;;

  *.java)
    if ! command_exists javac; then
      install_package "default-jdk"
    fi
    javac "$file" && java "$(basename "$file" .java)"
    rm -f "$(basename "$file" .java).class"
    ;;

  *.kt)
    if ! command_exists java; then
      echo "Installing Java..."
      install_package "default-jdk"
    fi
    if ! command_exists kotlinc; then
      echo "Installing Kotlin..."
      install_kotlin
    fi
    kotlinc "$file" -include-runtime -d temp.jar && java -jar temp.jar
    rm -f temp.jar
    ;;

  *.rs)
    if ! command_exists rustc; then
      install_rust
    fi
    rustc "$file" -o temp.out
    run_code ./temp.out
    ;;

  *.rb)
    if ! command_exists ruby; then
      install_package "ruby-full"
    fi
    ruby "$file"
    ;;

  *.php)
    if ! command_exists php; then
      install_package "php-cli"
    fi
    php "$file"
    ;;

  *.c)
    if ! command_exists gcc; then
      install_package "build-essential"
    fi
    gcc "$file" -o temp.out
    run_code ./temp.out
    ;;

  *.cpp|*.cc|*.cxx)
    if ! command_exists g++; then
      install_package "build-essential"
    fi
    g++ "$file" -o temp.out
    run_code ./temp.out
    ;;

  *.cs)
    if ! command_exists dotnet; then
      install_dotnet
    fi
    # Create a temporary project
    mkdir -p temp_cs_project
    cd temp_cs_project
    export DOTNET_GCHeapHardLimit=1C0000000
    dotnet new console --force
    cp "../$file" Program.cs
    dotnet run
    cd ..
    rm -rf temp_cs_project
    ;;

  *.sh|*.bash)
    chmod +x "$file"
    bash "$file"
    ;;

  *.zsh)
    if ! command_exists zsh; then
      install_package "zsh"
    fi
    chmod +x "$file"
    zsh "$file"
    ;;

  *.fish)
    if ! command_exists fish; then
      install_package "fish"
    fi
    chmod +x "$file"
    fish "$file"
    ;;

  *.pl)
    if ! command_exists perl; then
      install_package "perl"
    fi
    perl "$file"
    ;;

  *.lua)
    if ! command_exists lua; then
      install_package "lua5.3"
    fi
    lua "$file"
    ;;

  *.r|*.R)
    if ! command_exists Rscript; then
      install_package "r-base"
    fi
    Rscript "$file"
    ;;

  *.f90|*.f95|*.f03|*.f08)
    if ! command_exists gfortran; then
      install_package "gfortran"
    fi
    gfortran "$file" -o temp.out
    run_code ./temp.out
    ;;

  *.pas)
    if ! command_exists fpc; then
      install_package "fpc"
    fi
    fpc "$file" && "./$(basename "$file" .pas)"
    rm -f "$(basename "$file" .pas)" *.o
    ;;

  *.tcl)
    if ! command_exists tclsh; then
      install_package "tcl"
    fi
    tclsh "$file"
    ;;

  *.elm)
    if ! command_exists elm; then
      echo "Installing Elm..."
      if ! command_exists node; then
        install_nodejs
      fi
      npm install -g elm
    fi
    elm make "$file" --output=temp.html
    echo "Elm compiled to temp.html - transfer to browser to view"
    ;;

  *.fsx|*.fs)
    if ! command_exists dotnet; then
      install_dotnet
    fi
    dotnet fsi "$file"
    ;;
esac
