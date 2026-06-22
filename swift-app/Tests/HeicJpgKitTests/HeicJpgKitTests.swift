import XCTest
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers
@testable import HeicJpgKit

final class HeicJpgKitTests: XCTestCase {

    // MARK: targetURL rules

    func testTargetURLNextToSourceWhenNoOutputDir() {
        let converter = HeicConverter()
        let source = URL(fileURLWithPath: "/photos/IMG_0001.HEIC")
        let target = converter.targetURL(for: source, outputDirectory: nil)
        XCTAssertEqual(target.path, "/photos/IMG_0001.jpg")
    }

    func testTargetURLUnderOutputDir() {
        let converter = HeicConverter()
        let source = URL(fileURLWithPath: "/photos/sub/IMG_0002.heif")
        let outDir = URL(fileURLWithPath: "/out")
        let target = converter.targetURL(for: source, outputDirectory: outDir)
        XCTAssertEqual(target.path, "/out/IMG_0002.jpg")
    }

    func testDirectoryInputTargetPreservesRootAndRelativePath() {
        let converter = HeicConverter()
        let image = ScannedImage(
            source: URL(fileURLWithPath: "/photos/trip/day-1/IMG_0003.heic"),
            outputRelativePath: "trip/day-1/IMG_0003.heic"
        )

        let target = converter.targetURL(
            for: image,
            outputDirectory: URL(fileURLWithPath: "/out")
        )

        XCTAssertEqual(target.path, "/out/trip/day-1/IMG_0003.jpg")
    }

    // MARK: scanner

    func testScannerFindsHeicRecursivelyAndIgnoresOthers() throws {
        let fm = FileManager.default
        let root = fm.temporaryDirectory.appendingPathComponent("scan-\(UUID().uuidString)")
        let sub = root.appendingPathComponent("nested")
        try fm.createDirectory(at: sub, withIntermediateDirectories: true)
        defer { try? fm.removeItem(at: root) }

        try Data().write(to: root.appendingPathComponent("a.HEIC"))
        try Data().write(to: root.appendingPathComponent("b.png"))
        try Data().write(to: sub.appendingPathComponent("c.heif"))

        let found = ImageFileScanner().scan([root])
        let names = Set(found.map { $0.lastPathComponent })
        XCTAssertEqual(names, ["a.HEIC", "c.heif"])
    }

    func testScannerRetainsDirectoryRootForOutputMapping() throws {
        let fm = FileManager.default
        let root = fm.temporaryDirectory.appendingPathComponent("mapping-\(UUID().uuidString)")
        let sub = root.appendingPathComponent("nested")
        try fm.createDirectory(at: sub, withIntermediateDirectories: true)
        defer { try? fm.removeItem(at: root) }

        let source = sub.appendingPathComponent("photo.heic")
        try Data().write(to: source)

        let images = ImageFileScanner().scanImages([root])

        XCTAssertEqual(images.count, 1)
        XCTAssertEqual(images[0].source, source)
        XCTAssertEqual(
            images[0].outputRelativePath,
            "\(root.lastPathComponent)/nested/photo.heic"
        )
    }

    func testScannerDeduplicates() throws {
        let fm = FileManager.default
        let root = fm.temporaryDirectory.appendingPathComponent("dedup-\(UUID().uuidString)")
        try fm.createDirectory(at: root, withIntermediateDirectories: true)
        defer { try? fm.removeItem(at: root) }

        let file = root.appendingPathComponent("x.heic")
        try Data().write(to: file)

        let found = ImageFileScanner().scan([file, file, root])
        XCTAssertEqual(found.count, 1)
    }

    // MARK: skip-exists behavior (no decoding required)

    func testConvertSkipsWhenTargetExistsAndNoOverwrite() throws {
        let fm = FileManager.default
        let dir = fm.temporaryDirectory.appendingPathComponent("skip-\(UUID().uuidString)")
        try fm.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? fm.removeItem(at: dir) }

        let source = dir.appendingPathComponent("photo.heic")
        let target = dir.appendingPathComponent("photo.jpg")
        try Data().write(to: source)
        try Data("existing".utf8).write(to: target)

        let result = HeicConverter().convert(source: source, outputDirectory: nil,
                                             options: ConversionOptions(overwrite: false))
        XCTAssertEqual(result.outcome, .skippedExists)
        // The pre-existing file must be untouched.
        XCTAssertEqual(try Data(contentsOf: target), Data("existing".utf8))
    }

    func testOptionsClampQuality() {
        XCTAssertEqual(ConversionOptions(jpegQuality: 2.0).jpegQuality, 1.0)
        XCTAssertEqual(ConversionOptions(jpegQuality: -1.0).jpegQuality, 0.0)
    }

    // MARK: converted-source cleanup

    func testCleanupCandidatesRequireNonEmptyExpectedJPEG() throws {
        let fm = FileManager.default
        let dir = fm.temporaryDirectory.appendingPathComponent("cleanup-\(UUID().uuidString)")
        try fm.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? fm.removeItem(at: dir) }

        let validSource = dir.appendingPathComponent("valid.heic")
        let emptySource = dir.appendingPathComponent("empty.heif")
        try Data().write(to: validSource)
        try Data().write(to: emptySource)
        try Data("jpeg".utf8).write(to: dir.appendingPathComponent("valid.jpg"))
        try Data().write(to: dir.appendingPathComponent("empty.jpg"))

        let images = [validSource, emptySource].map {
            ScannedImage(source: $0, outputRelativePath: $0.lastPathComponent)
        }
        let candidates = ConvertedSourceCleaner().candidates(
            from: images,
            outputDirectory: nil
        )

        XCTAssertEqual(candidates.map(\.source), [validSource])
    }

    func testCleanupDeletesOnlyRevalidatedCandidates() throws {
        let fm = FileManager.default
        let dir = fm.temporaryDirectory.appendingPathComponent("delete-\(UUID().uuidString)")
        try fm.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? fm.removeItem(at: dir) }

        let source = dir.appendingPathComponent("photo.heic")
        let target = dir.appendingPathComponent("photo.jpg")
        try Data().write(to: source)
        try Data("jpeg".utf8).write(to: target)

        let cleaner = ConvertedSourceCleaner()
        let candidate = CleanupCandidate(source: source, target: target)
        let results = cleaner.delete([candidate])

        XCTAssertEqual(results.first?.outcome, .deleted)
        XCTAssertFalse(fm.fileExists(atPath: source.path))
        XCTAssertTrue(fm.fileExists(atPath: target.path))
    }


    func testConversionNormalizesOrientationIntoPixels() throws {
        let fm = FileManager.default
        let dir = fm.temporaryDirectory.appendingPathComponent("orientation-\(UUID().uuidString)")
        try fm.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? fm.removeItem(at: dir) }

        let source = dir.appendingPathComponent("rotated.heic")
        let target = dir.appendingPathComponent("rotated.jpg")
        try makeOrientedJPEG(width: 4, height: 2, orientation: 6, at: source)

        try HeicConverter().convertThrowing(
            source: source,
            target: target,
            options: ConversionOptions()
        )

        let output = try XCTUnwrap(CGImageSourceCreateWithURL(target as CFURL, nil))
        let properties = try XCTUnwrap(
            CGImageSourceCopyPropertiesAtIndex(output, 0, nil) as? [CFString: Any]
        )
        XCTAssertEqual(properties[kCGImagePropertyPixelWidth] as? Int, 2)
        XCTAssertEqual(properties[kCGImagePropertyPixelHeight] as? Int, 4)
        XCTAssertEqual(properties[kCGImagePropertyOrientation] as? Int, 1)
    }

    private func makeOrientedJPEG(
        width: Int,
        height: Int,
        orientation: Int,
        at url: URL
    ) throws {
        let colorSpace = try XCTUnwrap(CGColorSpace(name: CGColorSpace.sRGB))
        let context = try XCTUnwrap(CGContext(
            data: nil,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: 0,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
        ))
        context.setFillColor(CGColor(red: 0.2, green: 0.5, blue: 0.8, alpha: 1))
        context.fill(CGRect(x: 0, y: 0, width: width, height: height))

        let image = try XCTUnwrap(context.makeImage())
        let destination = try XCTUnwrap(CGImageDestinationCreateWithURL(
            url as CFURL,
            UTType.jpeg.identifier as CFString,
            1,
            nil
        ))
        CGImageDestinationAddImage(
            destination,
            image,
            [kCGImagePropertyOrientation: orientation] as CFDictionary
        )
        XCTAssertTrue(CGImageDestinationFinalize(destination))
    }
}
