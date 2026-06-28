import Foundation
import ImageIO
import UniformTypeIdentifiers

#if canImport(CoreGraphics)
import CoreGraphics
#endif

public enum ConversionError: Error, LocalizedError, Equatable {
    case cannotReadSource(URL)
    case cannotDecodeImage(URL)
    case cannotCreateDestination(URL)
    case writeFailed(URL)

    public var errorDescription: String? {
        switch self {
        case .cannotReadSource(let url):
            return "Could not read image: \(url.lastPathComponent)"
        case .cannotDecodeImage(let url):
            return "Could not decode image: \(url.lastPathComponent)"
        case .cannotCreateDestination(let url):
            return "Could not create output file: \(url.lastPathComponent)"
        case .writeFailed(let url):
            return "Failed to write: \(url.lastPathComponent)"
        }
    }
}

/// Converts HEIC/HEIF images to JPEG using ImageIO.
///
/// This is the cross-platform replacement for the CLI's `sips` call: identical
/// behavior on macOS and iOS, no external process required.
public struct HeicConverter: Sendable {
    public init() {}

    /// Computes the `.jpg` output URL for a source, mirroring the CLI's rules.
    /// - File next to source when `outputDirectory` is nil.
    /// - Directly under `outputDirectory` otherwise.
    public func targetURL(for source: URL, outputDirectory: URL?) -> URL {
        let baseName = source.deletingPathExtension().lastPathComponent
        let directory = outputDirectory ?? source.deletingLastPathComponent()
        return directory
            .appendingPathComponent(baseName)
            .appendingPathExtension("jpg")
    }

    /// Computes a target using the input kind retained by `ImageFileScanner`.
    /// Directory inputs preserve `<selected-directory>/...` under the output.
    public func targetURL(for image: ScannedImage, outputDirectory: URL?) -> URL {
        guard let outputDirectory else {
            return targetURL(for: image.source, outputDirectory: nil)
        }
        return outputDirectory
            .appendingPathComponent(image.outputRelativePath)
            .deletingPathExtension()
            .appendingPathExtension("jpg")
    }

    /// Non-throwing convenience that wraps the result/skip/failure outcomes.
    @discardableResult
    public func convert(
        source: URL,
        outputDirectory: URL? = nil,
        options: ConversionOptions = ConversionOptions()
    ) -> ConversionResult {
        let target = targetURL(for: source, outputDirectory: outputDirectory)

        if FileManager.default.fileExists(atPath: target.path) && !options.overwrite {
            return ConversionResult(source: source, target: target, outcome: .skippedExists)
        }

        do {
            try convertThrowing(source: source, target: target, options: options)
            return ConversionResult(source: source, target: target, outcome: .converted)
        } catch {
            let message = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            return ConversionResult(source: source, target: target, outcome: .failed(message))
        }
    }

    @discardableResult
    public func convert(
        image: ScannedImage,
        outputDirectory: URL? = nil,
        options: ConversionOptions = ConversionOptions()
    ) -> ConversionResult {
        let target = targetURL(for: image, outputDirectory: outputDirectory)

        if FileManager.default.fileExists(atPath: target.path) && !options.overwrite {
            return ConversionResult(source: image.source, target: target, outcome: .skippedExists)
        }

        do {
            try convertThrowing(source: image.source, target: target, options: options)
            return ConversionResult(source: image.source, target: target, outcome: .converted)
        } catch {
            let message = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            return ConversionResult(source: image.source, target: target, outcome: .failed(message))
        }
    }

    /// Core conversion. Throws on any failure.
    public func convertThrowing(
        source: URL,
        target: URL,
        options: ConversionOptions
    ) throws {
        guard let imageSource = CGImageSourceCreateWithURL(source as CFURL, nil) else {
            throw ConversionError.cannotReadSource(source)
        }

        let sourceProperties = CGImageSourceCopyPropertiesAtIndex(imageSource, 0, nil) as? [CFString: Any]
        let pixelWidth = sourceProperties?[kCGImagePropertyPixelWidth] as? Int ?? 0
        let pixelHeight = sourceProperties?[kCGImagePropertyPixelHeight] as? Int ?? 0
        let sourceMaxDimension = max(pixelWidth, pixelHeight)
        let requestedMaxDimension = options.maxDimension.flatMap { $0 > 0 ? $0 : nil }
        let thumbnailMaxDimension = requestedMaxDimension ?? sourceMaxDimension

        guard thumbnailMaxDimension > 0 else {
            throw ConversionError.cannotDecodeImage(source)
        }

        // Always materialize the EXIF orientation into the pixels. The output is
        // therefore upright even in consumers that ignore JPEG orientation tags.
        let thumbOptions: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: thumbnailMaxDimension
        ]
        guard let cgImage = CGImageSourceCreateThumbnailAtIndex(
            imageSource,
            0,
            thumbOptions as CFDictionary
        ) else {
            throw ConversionError.cannotDecodeImage(source)
        }

        let outputDir = target.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: outputDir, withIntermediateDirectories: true)

        let jpegType = UTType.jpeg.identifier as CFString
        guard let destination = CGImageDestinationCreateWithURL(target as CFURL, jpegType, 1, nil) else {
            throw ConversionError.cannotCreateDestination(target)
        }

        var destProperties: [CFString: Any] = [
            kCGImageDestinationLossyCompressionQuality: options.jpegQuality
        ]

        destProperties[kCGImagePropertyOrientation] = 1

        CGImageDestinationAddImage(destination, cgImage, destProperties as CFDictionary)

        guard CGImageDestinationFinalize(destination) else {
            throw ConversionError.writeFailed(target)
        }
    }
}
