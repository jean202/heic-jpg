import Foundation

/// Options that control a single conversion run.
///
/// Mirrors the Java CLI flags where it makes sense:
/// - `maxDimension`  ↔ `--max-dimension`
/// - `overwrite`     ↔ `--overwrite`
///
/// `jpegQuality` is app-specific (the `sips`-based CLI does not expose it).
public struct ConversionOptions: Sendable, Equatable {
    /// Longest-edge pixel cap. `nil` keeps the original resolution.
    public var maxDimension: Int?

    /// JPEG quality in the 0.0...1.0 range. 0.9 is a good default.
    public var jpegQuality: Double

    /// Replace an existing `.jpg` target instead of skipping it.
    public var overwrite: Bool

    public init(maxDimension: Int? = nil, jpegQuality: Double = 0.9, overwrite: Bool = false) {
        self.maxDimension = maxDimension
        self.jpegQuality = min(max(jpegQuality, 0.0), 1.0)
        self.overwrite = overwrite
    }
}
