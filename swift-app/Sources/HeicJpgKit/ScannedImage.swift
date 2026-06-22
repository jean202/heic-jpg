import Foundation

/// A source image plus the path used when an explicit output directory is set.
///
/// File inputs use only their file name. Directory inputs include the selected
/// directory name and preserve all descendants, matching the Java CLI planner.
public struct ScannedImage: Identifiable, Sendable, Equatable {
    public let source: URL
    public let outputRelativePath: String

    public init(source: URL, outputRelativePath: String) {
        self.source = source
        self.outputRelativePath = outputRelativePath
    }

    public var id: String { source.standardizedFileURL.path }
}
