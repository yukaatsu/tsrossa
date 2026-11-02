import SwiftUI
import AppKit

final class TsrossaAppDelegate: NSObject, NSApplicationDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApplication.shared.setActivationPolicy(.regular)
        TsrossaBrandLogo.applyMacDockIcon()
        NSApplication.shared.activate(ignoringOtherApps: true)
        if let window = NSApplication.shared.windows.first {
            WindowZoomManager.shared.configure(window: window)
            window.makeKeyAndOrderFront(nil)
            window.center()
        }
        DispatchQueue.main.async {
            if let window = NSApplication.shared.windows.first {
                WindowZoomManager.shared.configure(window: window)
            }
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
            CommandGroup(after: .windowSize) {
                Button("Fit to Screen (Zoom)") {
                    WindowZoomManager.shared.toggleFitToScreen()
                }
                .keyboardShortcut("z", modifiers: [.command, .option])
            }

            CommandMenu("File") {
                Button("Open Music Directory...") {
                    AppViewModel.shared.openFolderPicker()
                }
                .keyboardShortcut("o", modifiers: .command)

                Button("New Playlist...") {
                    AppViewModel.shared.openCreatePlaylistModal()
                }
                .keyboardShortcut("n", modifiers: .command)

                Button("Rescan Library") {
                    AppViewModel.shared.rescanCurrentFolder()
                }
                .keyboardShortcut("r", modifiers: .command)

                Divider()

                Button("Get Audio Track Info") {
                    AppViewModel.shared.showTrackDetailsModal.toggle()
                }
                .keyboardShortcut("i", modifiers: .command)
            }

            CommandMenu("Edit") {
                Button("Find in Music Library") {
                    AppViewModel.shared.focusSearchBar()
                }
                .keyboardShortcut("f", modifiers: .command)
            }

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

                Button("Seek Forward 5s") {
                    AudioEngine.shared.seek(to: AudioEngine.shared.currentTime + 5.0)
                }
                .keyboardShortcut(.rightArrow, modifiers: .option)

                Button("Seek Backward 5s") {
                    AudioEngine.shared.seek(to: max(0.0, AudioEngine.shared.currentTime - 5.0))
                }
                .keyboardShortcut(.leftArrow, modifiers: .option)

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
                    if AppViewModel.shared.repeatMode == .off {
                        AppViewModel.shared.repeatMode = .all
                    } else if AppViewModel.shared.repeatMode == .all {
                        AppViewModel.shared.repeatMode = .one
                    } else {
                        AppViewModel.shared.repeatMode = .off
                    }
                }
                .keyboardShortcut("t", modifiers: .command)

                Button("Toggle Like Track") {
                    if let cur = AudioEngine.shared.currentTrack {
                        AppViewModel.shared.toggleLike(track: cur)
                    }
                }
                .keyboardShortcut("l", modifiers: .command)

                Divider()

                Button("Audio Pipeline Diagnostics") {
                    AppViewModel.shared.showTelemetry.toggle()
                }
                .keyboardShortcut("u", modifiers: .command)
            }
            
            CommandMenu("Workspace") {
                Button("Previous Tab") {
                    AppViewModel.shared.selectPreviousTab()
                }
                .keyboardShortcut("[", modifiers: .command)

                Button("Next Tab") {
                    AppViewModel.shared.selectNextTab()
                }
                .keyboardShortcut("]", modifiers: .command)

                Divider()

                Button("Music Library") {
                    AppViewModel.shared.switchTab(to: .library)
                }
                .keyboardShortcut("1", modifiers: .command)
                
                Button("Playlists") {
                    AppViewModel.shared.switchTab(to: .playlist)
                }
                .keyboardShortcut("2", modifiers: .command)
                
                Button("DAC & Settings") {
                    AppViewModel.shared.switchTab(to: .settings)
                }
                .keyboardShortcut("3", modifiers: .command)
                
                Divider()

                Button("Toggle Now Playing Stage") {
                    withAnimation(.spring(response: 0.42, dampingFraction: 0.84)) {
                        AppViewModel.shared.isNowPlayingExpanded.toggle()
                    }
                }
                .keyboardShortcut("p", modifiers: .command)
                
                Divider()
                
                Button("System Logs & Diagnostics") {
                    AppViewModel.shared.showTelemetry.toggle()
                }
                .keyboardShortcut("d", modifiers: .command)
            }

            CommandGroup(replacing: .help) {
                Button("Keyboard Shortcuts Guide") {
                    AppViewModel.shared.showKeyboardShortcutsModal.toggle()
                }
                .keyboardShortcut("/", modifiers: .command)
            }
        }
    }
}
