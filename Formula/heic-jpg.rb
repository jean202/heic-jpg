# Homebrew formula for heic-jpg.
#
# This is a DRAFT formula for distribution via a personal tap.
# Before it works with `brew install`, you must:
#   1. Push a tagged release to GitHub (e.g. v0.1.0).
#   2. Update `url` to point at that release tarball.
#   3. Fill in `sha256` with the tarball's checksum:
#        curl -L https://github.com/jean202/heic-jpg/archive/refs/tags/v0.1.0.tar.gz | shasum -a 256
#   4. Host this file in a tap repo named `homebrew-heic-jpg`, then:
#        brew tap jean202/heic-jpg
#        brew install heic-jpg
#
# See docs/INSTALL.md for the full walkthrough.
class HeicJpg < Formula
  desc "Batch-convert HEIC/HEIF images to JPEG on macOS"
  homepage "https://github.com/jean202/heic-jpg"
  url "https://github.com/jean202/heic-jpg/archive/refs/tags/v0.1.0.tar.gz"
  sha256 "REPLACE_WITH_TARBALL_SHA256"
  license "MIT"
  version "0.1.0"

  depends_on "openjdk@17"
  depends_on :macos # relies on the system `sips` command at runtime

  def install
    java_home = Formula["openjdk@17"].opt_prefix
    ENV["JAVA_HOME"] = java_home
    ENV.prepend_path "PATH", "#{java_home}/bin"

    system "./scripts/build.sh"
    libexec.install "build/libs/heic-jpg-cli.jar"

    (bin/"heic-jpg").write <<~SH
      #!/bin/bash
      exec "#{java_home}/bin/java" -cp "#{libexec}/heic-jpg-cli.jar" \\
        io.github.jean202.heicjpg.Main "$@"
    SH

    (bin/"heic-jpg-ui").write <<~SH
      #!/bin/bash
      exec "#{java_home}/bin/java" -Xdock:name="HEIC JPG" \\
        -cp "#{libexec}/heic-jpg-cli.jar" \\
        io.github.jean202.heicjpg.HeicJpgUi "$@"
    SH
  end

  test do
    # --help exits 0 and prints usage; verifies the jar and wrapper work.
    assert_match "Usage", shell_output("#{bin}/heic-jpg --help")
  end
end
