{ pkgs, lib, config, inputs, ... }:

let clml = inputs.clojure-mcp-light;

in

{
  cachix.enable = false;

  # https://devenv.sh/basics/
  env.GREET = "devenv";
  env.CHROME_BIN = "${pkgs.chromium}/bin/chromium";

  # https://devenv.sh/packages/
  packages = [ pkgs.git pkgs.clojure pkgs.nodejs pkgs.chromedriver pkgs.chromium pkgs.babashka pkgs.bbin pkgs.parinfer-rust pkgs.nodejs];


  # https://devenv.sh/processes/
  processes.dev.exec = "clj -X:test:dev";
  processes.tailwind.exec = "npm run build:css";
  processes.tailwind.process-compose.is_tty = true;

  # https://devenv.sh/services/
  # services.postgres.enable = true;

  # https://devenv.sh/scripts/
  scripts.clj-paren-repair.exec = ''
    exec bb --config ${clml}/bb.edn -m clojure-mcp-light.paren-repair "$@"
  '';

  scripts.clj-nrepl-eval.exec = ''
    exec bb --config ${clml}/bb.edn -m clojure-mcp-light.nrepl-eval "$@"
  '';

  scripts.deploy.exec = ''
    clj -X:dev:test
    clj -T:build ci
    env $(cat ~/.secrets/.clojars | xargs) clj -T:build deploy
  '';

  # https://devenv.sh/basics/
  enterShell = ''
    echo "hello" &> /dev/null
  '';

  # https://devenv.sh/tasks/
  # tasks = {
  #   "myproj:setup".exec = "mytool build";
  #   "devenv:enterShell".after = [ "myproj:setup" ];
  # };

  # https://devenv.sh/tests/
  enterTest = ''
    echo "Running tests"
    git --version | grep --color=auto "${pkgs.git.version}"
  '';

  # https://devenv.sh/git-hooks/
  # git-hooks.hooks.shellcheck.enable = true;

  # See full reference at https://devenv.sh/reference/options/
}
