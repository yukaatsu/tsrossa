import Foundation

public enum RepeatMode: String, Codable, CaseIterable {
    case off
    case all
    case one
}

public struct PlaybackQueueState {
    public var tracks: [AudioTrack]
    public var currentIndex: Int
    public var repeatMode: RepeatMode
    public var isShuffle: Bool
    public var shuffleDeck: [Int]
    public var playbackHistory: [AudioTrack]
    public var priorityQueue: [AudioTrack]

    public init(
        tracks: [AudioTrack] = [],
        currentIndex: Int = 0,
        repeatMode: RepeatMode = .off,
        isShuffle: Bool = false,
        shuffleDeck: [Int] = [],
        playbackHistory: [AudioTrack] = [],
        priorityQueue: [AudioTrack] = []
    ) {
        self.tracks = tracks
        self.currentIndex = currentIndex
        self.repeatMode = repeatMode
        self.isShuffle = isShuffle
        self.shuffleDeck = shuffleDeck
        self.playbackHistory = playbackHistory
        self.priorityQueue = priorityQueue
    }

    public var currentTrack: AudioTrack? {
        guard currentIndex >= 0 && currentIndex < tracks.count else { return nil }
        return tracks[currentIndex]
    }
}

public enum QueueHelper {
    public static func buildShuffleDeck(count: Int, excluding: Int?) -> [Int] {
        guard count > 0 else { return [] }
        var deck = Array(0..<count)
        if let excl = excluding, count > 1 {
            deck.removeAll { $0 == excl }
        }
        // Fisher-Yates shuffle
        for i in (1..<deck.count).reversed() {
            let j = Int.random(in: 0...i)
            if i != j {
                deck.swapAt(i, j)
            }
        }
        return deck
    }

    public static func resolveNextTrack(state: inout PlaybackQueueState, userInitiated: Bool) -> AudioTrack? {
        // 1. Priority Queue takes highest precedence
        if !state.priorityQueue.isEmpty {
            if let curr = state.currentTrack {
                state.playbackHistory.append(curr)
            }
            let next = state.priorityQueue.removeFirst()
            if let idx = state.tracks.firstIndex(where: { $0.path == next.path }) {
                state.currentIndex = idx
            }
            return next
        }

        guard !state.tracks.isEmpty else { return nil }

        // 2. RepeatMode.one: if automatic advance (end of track), replay same track
        if state.repeatMode == .one && !userInitiated {
            return state.currentTrack
        }

        // 3. Shuffle Mode
        if state.isShuffle {
            if state.shuffleDeck.isEmpty {
                // If deck is exhausted
                if state.repeatMode == .all || userInitiated {
                    state.shuffleDeck = buildShuffleDeck(count: state.tracks.count, excluding: state.currentIndex)
                } else {
                    return nil
                }
            }

            guard !state.shuffleDeck.isEmpty else { return nil }
            let nextIndex = state.shuffleDeck.removeFirst()

            if let curr = state.currentTrack {
                state.playbackHistory.append(curr)
            }
            state.currentIndex = nextIndex
            return state.currentTrack
        }

        // 4. Sequential Mode
        let nextIndex = state.currentIndex + 1
        if nextIndex >= state.tracks.count {
            if state.repeatMode == .all {
                if let curr = state.currentTrack {
                    state.playbackHistory.append(curr)
                }
                state.currentIndex = 0
                return state.currentTrack
            } else {
                return nil
            }
        }

        if let curr = state.currentTrack {
            state.playbackHistory.append(curr)
        }
        state.currentIndex = nextIndex
        return state.currentTrack
    }

    public static func resolvePreviousTrack(state: inout PlaybackQueueState, currentTime: Double) -> (track: AudioTrack?, restartOnly: Bool) {
        // If track played for more than 3 seconds, jump back to start of current track
        if currentTime > 3.0 {
            return (nil, true)
        }

        // Pop from playback history
        if !state.playbackHistory.isEmpty {
            let prevTrack = state.playbackHistory.removeLast()
            if let idx = state.tracks.firstIndex(where: { $0.path == prevTrack.path }) {
                state.currentIndex = idx
            }
            return (prevTrack, false)
        }

        // If history is empty, step back sequentially
        if state.currentIndex > 0 {
            state.currentIndex -= 1
            return (state.currentTrack, false)
        } else if state.repeatMode == .all && !state.tracks.isEmpty {
            state.currentIndex = state.tracks.count - 1
            return (state.currentTrack, false)
        }

        return (nil, true)
    }
}
