FROM ubuntu:latest
SHELL ["/bin/bash", "-lc"]

ARG DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
 && apt-get install -y --no-install-recommends openjdk-17-jdk maven \
 && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:${PATH}

WORKDIR /workspace

RUN install -d /usr/local/bin

RUN cat <<'PY' >/usr/local/bin/loan-ms-bootstrap-build
#!/usr/bin/env python3
import re
import shutil
import sys
import textwrap
import xml.etree.ElementTree as ET
from pathlib import Path

NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def fail(message: str) -> None:
    print(f"[loan-ms-bootstrap-build] {message}", file=sys.stderr)
    raise SystemExit(1)


def get_text(node, path: str, default=None):
    element = node.find(path, NS)
    if element is not None and element.text and element.text.strip():
        return element.text.strip()
    return default


def write_file(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(textwrap.dedent(content).strip() + "\n", encoding="utf-8")


def dependency_xml(group_id: str, artifact_id: str, version: str | None = None, optional: bool = False, type_: str | None = None, scope: str | None = None) -> str:
    lines = [
        "        <dependency>",
        f"            <groupId>{group_id}</groupId>",
        f"            <artifactId>{artifact_id}</artifactId>",
    ]
    if version is not None:
        lines.append(f"            <version>{version}</version>")
    if type_ is not None:
        lines.append(f"            <type>{type_}</type>")
    if scope is not None:
        lines.append(f"            <scope>{scope}</scope>")
    if optional:
        lines.append("            <optional>true</optional>")
    lines.append("        </dependency>")
    return "\n".join(lines)


def java_files_under(directory: Path) -> list[Path]:
    if not directory.exists():
        return []
    files: list[Path] = []
    for path in sorted(directory.rglob("*.java")):
        if any(part in {".git", ".cosine", "target"} for part in path.parts):
            continue
        files.append(path)
    return files


def detect_package(java_file: Path) -> str:
    for line in java_file.read_text(encoding="utf-8").splitlines():
        match = re.match(r"\s*package\s+([A-Za-z0-9_.]+)\s*;\s*$", line)
        if match:
            return match.group(1)
    return ""


def copy_java_file(source: Path, module_output_dir: Path) -> None:
    destination_dir = module_output_dir / "src" / "main" / "java"
    package_name = detect_package(source)
    if package_name:
        destination_dir = destination_dir / package_name.replace(".", "/")
    destination_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination_dir / source.name)


def render_module_pom(group_id: str, root_artifact_id: str, version: str, artifact_id: str, dependencies: list[str]) -> str:
    dependencies_section = ""
    if dependencies:
        dependencies_section = "\n    <dependencies>\n" + "\n".join(dependencies) + "\n    </dependencies>\n"
    return f"""
<project xmlns=\"http://maven.apache.org/POM/4.0.0\"
         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>{group_id}</groupId>
        <artifactId>{root_artifact_id}</artifactId>
        <version>{version}</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>{artifact_id}</artifactId>
    <name>{artifact_id}</name>{dependencies_section}</project>
"""


def main() -> None:
    repository_root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    output_root = Path(sys.argv[2] if len(sys.argv) > 2 else repository_root / ".cosine" / "generated-maven-build").resolve()

    pom_path = repository_root / "pom.xml"
    if not pom_path.exists():
        fail(f"Missing root pom.xml at {pom_path}")

    project = ET.parse(pom_path).getroot()

    group_id = get_text(project, "m:groupId") or get_text(project, "m:parent/m:groupId", "com.bank")
    root_artifact_id = get_text(project, "m:artifactId", "reactive-loan-management")
    version = get_text(project, "m:version", "1.0.0")
    boot_version = get_text(project, "m:parent/m:version", "3.1.5")
    java_version = get_text(project, "m:properties/m:java.version", "17")
    module_names = [module.text.strip() for module in project.findall("m:modules/m:module", NS) if module.text and module.text.strip()]

    cloud_version = None
    for dependency in project.findall("m:dependencyManagement/m:dependencies/m:dependency", NS):
        if get_text(dependency, "m:artifactId") == "spring-cloud-dependencies":
            cloud_version = get_text(dependency, "m:version")
            break

    root_level_java_files = sorted(path for path in repository_root.glob("*.java") if path.is_file())
    generated_modules = list(module_names)
    if root_level_java_files and "loan-demo" not in generated_modules:
        generated_modules.append("loan-demo")

    module_sources: dict[str, list[Path]] = {}
    for module_name in module_names:
        module_sources[module_name] = java_files_under(repository_root / module_name)

    if output_root.exists():
        shutil.rmtree(output_root)
    output_root.mkdir(parents=True, exist_ok=True)

    modules_xml = "\n".join(f"        <module>{module_name}</module>" for module_name in generated_modules)
    dependency_management_xml = ""
    if cloud_version:
        dependency_management_xml = f"""
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>{cloud_version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
"""

    write_file(output_root / "pom.xml", f"""
<project xmlns=\"http://maven.apache.org/POM/4.0.0\"
         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">
    <modelVersion>4.0.0</modelVersion>

    <groupId>{group_id}</groupId>
    <artifactId>{root_artifact_id}</artifactId>
    <version>{version}</version>
    <packaging>pom</packaging>
    <name>Reactive Loan Management System (generated build)</name>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>{boot_version}</version>
        <relativePath/>
    </parent>

    <properties>
        <java.version>{java_version}</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <modules>
{modules_xml}
    </modules>{dependency_management_xml}</project>
""")

    for module_name in module_names:
        dependencies: list[str] = []
        if module_name == "common" and module_sources[module_name]:
            dependencies.append(dependency_xml("com.fasterxml.jackson.core", "jackson-annotations"))
            dependencies.append(dependency_xml("org.projectlombok", "lombok", optional=True))
        elif module_sources[module_name]:
            if "common" in module_names:
                dependencies.append(dependency_xml(group_id, "common", "${project.version}"))
            dependencies.append(dependency_xml("org.springframework.boot", "spring-boot-starter-webflux"))
            dependencies.append(dependency_xml("org.projectlombok", "lombok", optional=True))

        write_file(
            output_root / module_name / "pom.xml",
            render_module_pom(group_id, root_artifact_id, version, module_name, dependencies),
        )

        for source in module_sources[module_name]:
            copy_java_file(source, output_root / module_name)

    if root_level_java_files:
        loan_demo_dependencies: list[str] = []
        if "common" in module_names:
            loan_demo_dependencies.append(dependency_xml(group_id, "common", "${project.version}"))
        for module_name in module_names:
            if module_name != "common" and module_sources[module_name]:
                loan_demo_dependencies.append(dependency_xml(group_id, module_name, "${project.version}"))
        loan_demo_dependencies.append(dependency_xml("org.springframework.boot", "spring-boot-starter-webflux"))
        loan_demo_dependencies.append(dependency_xml("org.projectlombok", "lombok", optional=True))

        write_file(output_root / "loan-demo" / "pom.xml", f"""
<project xmlns=\"http://maven.apache.org/POM/4.0.0\"
         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>{group_id}</groupId>
        <artifactId>{root_artifact_id}</artifactId>
        <version>{version}</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>loan-demo</artifactId>
    <name>loan-demo</name>

    <dependencies>
{"\n".join(loan_demo_dependencies)}
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
            </plugin>
        </plugins>
    </build>
</project>
""")

        for source in root_level_java_files:
            copy_java_file(source, output_root / "loan-demo")

    print(output_root)


if __name__ == "__main__":
    main()
PY

RUN chmod +x /usr/local/bin/loan-ms-bootstrap-build

RUN cat <<'SH' >/usr/local/bin/mvn
#!/usr/bin/env bash
set -euo pipefail

REAL_MVN=/usr/bin/mvn

for arg in "$@"; do
  case "$arg" in
    -f|--file|-h|--help|-v|-version|--version)
      exec "$REAL_MVN" "$@"
      ;;
  esac
done

if [[ "${MAVEN_SKIP_LOAN_BOOTSTRAP:-0}" == "1" ]]; then
  exec "$REAL_MVN" "$@"
fi

find_repo_root() {
  local dir="$PWD"
  while [[ "$dir" != "/" ]]; do
    if [[ -f "$dir/pom.xml" ]]; then
      printf '%s\n' "$dir"
      return 0
    fi
    dir="$(dirname "$dir")"
  done
  return 1
}

should_bootstrap_repo() {
  local repo_root="$1"
  [[ -f "$repo_root/pom.xml" ]] || return 1
  [[ -f "$repo_root/LoanApplicationDemo.java" ]] || return 1
  [[ -f "$repo_root/commercial-service/CommercialScoringService.java" ]] || return 1
  [[ -f "$repo_root/risk-service/RiskAnalysisService.java" ]] || return 1
  [[ -f "$repo_root/credit-service/LoanDecisionService.java" ]] || return 1
  [[ -f "$repo_root/notification-service/NotificationService.java" ]] || return 1
  [[ -f "$repo_root/common/DomainEvent.java" ]] || return 1
  [[ ! -f "$repo_root/commercial-service/pom.xml" ]] || return 1
  [[ ! -f "$repo_root/risk-service/pom.xml" ]] || return 1
  [[ ! -f "$repo_root/credit-service/pom.xml" ]] || return 1
  [[ ! -f "$repo_root/notification-service/pom.xml" ]] || return 1
  return 0
}

if repo_root="$(find_repo_root 2>/dev/null)"; then
  if [[ "$repo_root" != *"/.cosine/generated-maven-build"* ]] && should_bootstrap_repo "$repo_root"; then
    generated_dir="$repo_root/.cosine/generated-maven-build"
    loan-ms-bootstrap-build "$repo_root" "$generated_dir" >/dev/null
    echo "[loan-ms-java17] Bootstrapped generated Maven workspace at $generated_dir" >&2
    exec "$REAL_MVN" -f "$generated_dir/pom.xml" "$@"
  fi
fi

exec "$REAL_MVN" "$@"
SH

RUN chmod +x /usr/local/bin/mvn
