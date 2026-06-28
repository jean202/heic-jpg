import Foundation

/// The outcome of attempting to convert one source file.
public struct ConversionResult: Identifiable, Sendable, Equatable {
    public enum Outcome: Sendable, Equatable {
        /// A new `.jpg` was written.
        case converted
        /// The target already existed and `overwrite` was off.
        case skippedExists
        /// Conversion failed; the associated value is a human-readable reason.
        case failed(String)
    }

    public let id: UUID
    public let source: URL
    public let target: URL?
    public let outcome: Outcome

    public init(id: UUID = UUID(), source: URL, target: URL?, outcome: Outcome) {
        self.id = id
        self.source = source
        self.target = target
        self.outcome = outcome
    }

    public var didConvert: Bool {
        if case .converted = outcome { return true }
        return false
    }

    public var didFail: Bool {
        if case .failed = outcome { return true }
        return false
    }
}
