import SwiftUI
import AppKit

final class TsrossaAppDelegate: NSObject, NSApplicationDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApplication.shared.setActivationPolicy(.regular)
        TsrossaBrandLogo.applyMacDockIcon()
        NSApplication.shared.activate(ignoringOtherApps: true)
        if let window = NSApplication.shared.windows.first {
            window.makeKeyAndOrderFront(nil)
            window.center()
        }
    }
    
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        return true
    }
}

@main
struct TsrossaMacApp: App {
    @NSApplicationDelegateAdaptor(TsrossaAppDelegate.self) var appDelegate
    
    var body: some Scene {
        WindowGroup {
            MainAppView()
        }
        .windowStyle(HiddenTitleBarWindowStyle())
        .commands {
            CommandMenu("Playback") {
                Button("Play / Pause") {
                    AudioEngine.shared.togglePlayPause()
                }
                .keyboardShortcut(.space, modifiers: [])
                
                Button("Next Track") {
                    AppViewModel.shared.playNextTrack()
                }
                .keyboardShortcut(.rightArrow, modifiers: .command)
                
                Button("Previous Track") {
                    AppViewModel.shared.playPreviousTrack()
                }
                .keyboardShortcut(.leftArrow, modifiers: .command)
                
                Divider()
                
                Button("Volume Up (+1 dBFS)") {
                    AppViewModel.shared.volumeUp()
                }
                .keyboardShortcut(.upArrow, modifiers: .command)
                
                Button("Volume Down (-1 dBFS)") {
                    AppViewModel.shared.volumeDown()
                }
                .keyboardShortcut(.downArrow, modifiers: .command)
                
                Divider()
                
                Button("Toggle Shuffle") {
                    AppViewModel.shared.isShuffle.toggle()
                }
                .keyboardShortcut("s", modifiers: .command)
                
                Button("Toggle Repeat") {
                    AppViewModel.shared.isRepeat.toggle()
                }
                .keyboardShortcut("t", modifiers: .command)
            }
            
            CommandMenu("Workspace") {
                Button("Now Playing") {
                    AppViewModel.shared.activeTab = .nowPlaying
                }
                .keyboardShortcut("1", modifiers: .command)
                
                Button("Music Library") {
                    AppViewModel.shared.activeTab = .library
                }
                .keyboardShortcut("2", modifiers: .command)
                
                Button("Play Queue") {
                    AppViewModel.shared.activeTab = .playlist
                }
                .keyboardShortcut("3", modifiers: .command)
                
                Button("DAC & Settings") {
                    AppViewModel.shared.activeTab = .settings
                }
                .keyboardShortcut("4", modifiers: .command)
                
                Divider()
                
                Button("System Logs & Diagnostics") {
                    AppViewModel.shared.showTelemetry.toggle()
                }
                .keyboardShortcut("d", modifiers: .command)
            }
        }
    }
}
