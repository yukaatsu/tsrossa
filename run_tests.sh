#!/bin/bash
set -euo pipefail

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
TEST_BUILD_DIR="$DIR/build/tests"
mkdir -p "$TEST_BUILD_DIR"

echo "========================================="
echo "  TSRossa macOS Automated Test Runner"
echo "========================================="

# 1. C++ Unit Tests (jika ada di tests/cpp/)
if ls "$DIR/tests/cpp/"*.cpp >/dev/null 2>&1; then
    for cpp_test in "$DIR/tests/cpp/"*.cpp; do
        test_name="$(basename "$cpp_test" .cpp)"
        echo "==> Compiling C++ test: $test_name"
        clang++ -std=c++17 -O2 -I"$DIR/src/nativeMain/cpp" \
            "$DIR/src/nativeMain/cpp/dr_impl.c" \
            "$cpp_test" \
            -framework CoreAudio -framework AudioToolbox -framework AudioUnit \
            -o "$TEST_BUILD_DIR/$test_name"
        "$TEST_BUILD_DIR/$test_name"
    done
fi

# 2. Swift Unit Tests
echo "==> Compiling Swift tests..."
cat > "$TEST_BUILD_DIR/main_test.swift" <<EOF
import Foundation

@main
struct MainTestRunner {
    static func main() {
        runUpdateVersionTests()
        runAudioTagReaderTests()
        runColorAndThemeTests()
        runSortAndFuzzySearchTests()
        if TestSuite.failedCount > 0 {
            print("\nRESULT: \(TestSuite.failedCount) FAILED, \(TestSuite.passedCount) PASSED")
            Foundation.exit(1)
        } else {
            print("\nRESULT: ALL \(TestSuite.passedCount) SWIFT TESTS PASSED")
        }
    }
}
EOF

SWIFT_SOURCES=()
while IFS= read -r -d '' f; do
    SWIFT_SOURCES+=("$f")
done < <(find "$DIR/tests/swift" -name "*.swift" -print0 | sort -z)

swiftc -O \
    "$DIR/macos/Sources/UpdateManager.swift" \
    "$DIR/macos/Sources/AudioTagReader.swift" \
    "$DIR/macos/Sources/ColorContrastHelper.swift" \
    "$DIR/macos/Sources/FuzzySearchHelper.swift" \
    "$DIR/macos/Sources/AlbumArtCache.swift" \
    "$DIR/macos/Sources/TrackMetadata.swift" \
    "$DIR/macos/Sources/QueueHelper.swift" \
    "${SWIFT_SOURCES[@]}" \
    "$TEST_BUILD_DIR/main_test.swift" \
    -framework Cocoa \
    -o "$TEST_BUILD_DIR/swift_tests"

"$TEST_BUILD_DIR/swift_tests"
