import Foundation

public struct TestFailure: Error, CustomStringConvertible {
    public let message: String
    public let file: String
    public let line: Int
    public var description: String { "\(file):\(line) - \(message)" }
}

public final class TestSuite {
    public static var passedCount = 0
    public static var failedCount = 0

    public static func run(_ name: String, _ block: () throws -> Void) {
        do {
            try block()
            passedCount += 1
            print("  [PASS] \(name)")
        } catch {
            failedCount += 1
            print("  [FAIL] \(name): \(error)")
        }
    }

    public static func assertEqual<T: Equatable>(_ actual: T, _ expected: T, _ message: String = "", file: String = #fileID, line: Int = #line) throws {
        if actual != expected {
            throw TestFailure(message: "Expected \(expected), got \(actual). \(message)", file: file, line: line)
        }
    }

    public static func assertTrue(_ condition: Bool, _ message: String = "", file: String = #fileID, line: Int = #line) throws {
        if !condition {
            throw TestFailure(message: "Assertion failed. \(message)", file: file, line: line)
        }
    }

    public static func assertFalse(_ condition: Bool, _ message: String = "", file: String = #fileID, line: Int = #line) throws {
        if condition {
            throw TestFailure(message: "Expected false, got true. \(message)", file: file, line: line)
        }
    }
}
