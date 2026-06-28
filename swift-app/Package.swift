// swift-tools-version: 5.9
import PackageDescription

// HeicJpgKit — the cross-platform conversion core shared by the macOS and iOS apps.
// Unlike the Java CLI (which shells out to macOS `sips`), this core uses ImageIO /
// Core Graphics so the exact same code runs on both macOS and iOS.
let package = Package(
    name: "HeicJpgKit",
    platforms: [
        .macOS(.v12),
        .iOS(.v15)
    ],
    products: [
        .library(name: "HeicJpgKit", targets: ["HeicJpgKit"])
    ],
    targets: [
        .target(name: "HeicJpgKit"),
        .testTarget(name: "HeicJpgKitTests", dependencies: ["HeicJpgKit"])
    ]
)
