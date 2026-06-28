import Foundation

public struct CleanupCandidate: Identifiable, Sendable, Equatable {
    public let source: URL
    public let target: URL

    public init(source: URL, target: URL) {
        self.source = source
        self.target = target
    }

    public var id: String { source.standardizedFileURL.path }
}

public struct CleanupResult: Identifiable, Sendable, Equatable {
    public enum Outcome: Sendable, Equatable {
        case deleted
        case kept(String)
        case failed(String)
    }

    public let source: URL
    public let outcome: Outcome

    public init(source: URL, outcome: Outcome) {
        self.source = source
        self.outcome = outcome
    }

    public var id: String { source.standardizedFileURL.path }
}

/// Implements the CLI's `--delete-converted` safety rule for the apps.
public struct ConvertedSourceCleaner: Sendable {
    private let converter = HeicConverter()

    public init() {}

    /// Returns only HEIC/HEIF sources whose expected JPEG target exists and is non-empty.
    public func candidates(
        from images: [ScannedImage],
        outputDirectory: URL?
    ) -> [CleanupCandidate] {
        images.compactMap { image in
            guard Self.isSupportedSource(image.source) else { return nil }
            let target = converter.targetURL(for: image, outputDirectory: outputDirectory)
            guard Self.hasConvertedJPEG(at: target) else { return nil }
            return CleanupCandidate(source: image.source, target: target)
        }
    }

    /// Revalidates every matching JPEG immediately before deleting its source.
    public func delete(_ candidates: [CleanupCandidate]) -> [CleanupResult] {
        candidates.map { candidate in
            guard Self.isSupportedSource(candidate.source) else {
                return CleanupResult(source: candidate.source, outcome: .kept("Not a HEIC/HEIF file"))
            }
            guard Self.hasConvertedJPEG(at: candidate.target) else {
                return CleanupResult(source: candidate.source, outcome: .kept("Matching JPEG is missing or empty"))
            }

            do {
                try FileManager.default.removeItem(at: candidate.source)
                return CleanupResult(source: candidate.source, outcome: .deleted)
            } catch {
                return CleanupResult(source: candidate.source, outcome: .failed(error.localizedDescription))
            }
        }
    }

    private static func isSupportedSource(_ url: URL) -> Bool {
        ImageFileScanner.supportedExtensions.contains(url.pathExtension.lowercased())
    }

    private static func hasConvertedJPEG(at url: URL) -> Bool {
        do {
            let values = try url.resourceValues(forKeys: [.isRegularFileKey, .fileSizeKey])
            return values.isRegularFile == true && (values.fileSize ?? 0) > 0
        } catch {
            return false
        }
    }
}
