import Foundation

/// Expands a mix of file and directory URLs into a flat list of HEIC/HEIF files.
/// Directories are walked recursively, matching the Java CLI's behavior.
public struct ImageFileScanner: Sendable {
    public static let supportedExtensions: Set<String> = ["heic", "heif"]

    public init() {}

    public func scan(_ urls: [URL]) -> [URL] {
        scanImages(urls).map(\.source)
    }

    /// Scans inputs while retaining the CLI-compatible relative output path.
    public func scanImages(_ urls: [URL]) -> [ScannedImage] {
        var found: [ScannedImage] = []
        var seen: Set<String> = []
        let fm = FileManager.default

        func appendIfNew(_ url: URL, outputRelativePath: String) {
            let key = url.standardizedFileURL.path
            if seen.insert(key).inserted {
                found.append(ScannedImage(source: url, outputRelativePath: outputRelativePath))
            }
        }

        for url in urls {
            var isDirectory: ObjCBool = false
            guard fm.fileExists(atPath: url.path, isDirectory: &isDirectory) else { continue }

            if isDirectory.boolValue {
                let rootName = url.lastPathComponent
                let resolvedRootComponents = url.resolvingSymlinksInPath().pathComponents
                let enumerator = fm.enumerator(
                    at: url,
                    includingPropertiesForKeys: [.isRegularFileKey],
                    options: [.skipsHiddenFiles]
                )
                while let item = enumerator?.nextObject() as? URL {
                    if Self.isSupported(item) {
                        let itemComponents = item.resolvingSymlinksInPath().pathComponents
                        guard itemComponents.starts(with: resolvedRootComponents) else { continue }
                        let relative = itemComponents
                            .dropFirst(resolvedRootComponents.count)
                            .joined(separator: "/")
                        let outputPath = rootName.isEmpty ? relative : rootName + "/" + relative
                        appendIfNew(
                            url.appendingPathComponent(relative),
                            outputRelativePath: outputPath
                        )
                    }
                }
            } else if Self.isSupported(url) {
                appendIfNew(url, outputRelativePath: url.lastPathComponent)
            }
        }
        return found.sorted { $0.source.path < $1.source.path }
    }

    private static func isSupported(_ url: URL) -> Bool {
        supportedExtensions.contains(url.pathExtension.lowercased())
    }
}
