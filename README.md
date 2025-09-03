# 📥 Advanced Video Downreamer

A powerful Android download manager designed for downloading video files, audio files, and other media content from web directories and direct URLs.

## ✨ Features

### 🎯 Core Functionality
- **Multi-format Support**: Download videos, audio files, documents, and more
- **Batch Downloads**: Select and download multiple files simultaneously
- **Progress Tracking**: Real-time download progress with visual indicators
- **Status Monitoring**: Clear status indicators (Starting, Downloading, Complete, Failed)
- **Smart Detection**: Automatically detects file types and sizes

### 🚀 Advanced Features
- **Directory Browsing**: Fetch files from web directories and HTML pages
- **Direct URL Support**: Download individual files from direct links
- **Storage Management**: Choose download location (internal storage or SD card)
- **File Filtering**: Search and filter files by name
- **Playlist Creation**: Generate M3U playlists for downloaded audio/video files
- **Streaming Support**: Stream files directly without downloading

### 🎨 User Experience
- **Modern UI**: Clean, Material Design interface
- **Dark Theme**: Optimized for low-light usage
- **Responsive Design**: Smooth animations and transitions
- **Non-flashing Progress**: Stable progress bars without visual artifacts
- **Targeted Updates**: Efficient UI updates that don't refresh entire elements

## 📱 Screenshots

*Screenshots will be added here showing the main interface, download progress, and file management features.*

## 🛠️ Installation

### Prerequisites
- Android 7.0 (API level 24) or higher
- Internet connection for downloading files
- Storage permissions for saving downloaded files

### Building from Source

1. **Clone the repository**
   ```bash
   git clone https://github.com/hyperionsolitude/Advanced_Video_Downreamer.git
   cd Advanced_Video_Downreamer
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Open the project folder
   - Wait for Gradle sync to complete

3. **Build and Install**
   ```bash
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```

### APK Installation
Download the latest APK from the [Releases](https://github.com/hyperionsolitude/Advanced_Video_Downreamer/releases) page and install on your Android device.

## 🚀 Usage

### Basic Usage

1. **Enter URL**: Input a direct file URL or directory URL
2. **Fetch Files**: Tap "🔍 Fetch Files" to discover available files
3. **Select Files**: Check the files you want to download
4. **Start Download**: Tap "📥 Download Selected" to begin downloads
5. **Monitor Progress**: Watch real-time progress and status updates

### Advanced Features

#### Directory Browsing
- Enter a directory URL (e.g., `https://example.com/videos/`)
- The app will scan for downloadable files
- Filter results using the search box

#### Batch Operations
- **Select All**: Use "Select All" to choose all files
- **Invert Selection**: Toggle selection of all files
- **Clear Selection**: Deselect all files

#### Storage Management
- **Change Storage**: Tap "Change Storage" to select download location
- **Storage Info**: View available space and current storage location

#### File Streaming
- **Stream Single File**: Select one file and tap "▶️ Stream"
- **Create Playlist**: Select multiple audio/video files and tap "📋 Create Playlist"

## 🏗️ Technical Details

### Architecture
- **Language**: Kotlin
- **UI Framework**: Android Views with Material Design Components
- **Architecture Pattern**: MVVM (Model-View-ViewModel)
- **Async Operations**: Kotlin Coroutines
- **File Management**: Android FileProvider API

### Key Components
- **MainActivity**: Main UI and download orchestration
- **FileAdapter**: RecyclerView adapter for file list display
- **MainViewModel**: Business logic and data management
- **DownloadFile**: Data model for file information
- **FileUtils**: Utility functions for file operations

### Performance Optimizations
- **Throttled UI Updates**: Progress updates limited to prevent UI lag
- **Targeted View Updates**: Only progress bars update, not entire list items
- **Efficient Memory Usage**: Proper cleanup of resources and handlers
- **Background Processing**: Downloads run on background threads

## 📋 Supported File Types

### Video Formats
- MP4, AVI, MKV, MOV, WMV, FLV, WebM, 3GP

### Audio Formats
- MP3, WAV, FLAC, AAC, OGG, M4A

### Document Formats
- PDF, DOC, DOCX, TXT, RTF

### Archive Formats
- ZIP, RAR, 7Z, TAR, GZ

## 🔧 Configuration

### Storage Settings
- **Default Location**: Downloads folder
- **Custom Location**: Choose any accessible directory
- **SD Card Support**: Full external storage support

### Download Settings
- **Concurrent Downloads**: Multiple files download simultaneously
- **Retry Logic**: Automatic retry on network errors
- **Progress Updates**: Real-time progress tracking

## 🐛 Troubleshooting

### Common Issues

**Downloads not starting**
- Check internet connection
- Verify URL is accessible
- Ensure storage permissions are granted

**Files not appearing**
- Verify the URL points to a directory or file
- Check if the server allows directory listing
- Try refreshing the file list

**Storage issues**
- Ensure sufficient storage space
- Check storage permissions
- Try changing download location

**App crashes**
- Restart the app
- Clear app data if necessary
- Check Android version compatibility

## 🤝 Contributing

We welcome contributions! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Development Guidelines
- Follow Kotlin coding conventions
- Add comments for complex logic
- Test on multiple Android versions
- Update documentation for new features

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Material Design Components for Android
- Kotlin Coroutines for async operations
- Android FileProvider for secure file access
- Jsoup for HTML parsing

## 📞 Support

- **Issues**: Report bugs and request features on [GitHub Issues](https://github.com/hyperionsolitude/Advanced_Video_Downreamer/issues)
- **Discussions**: Join community discussions on [GitHub Discussions](https://github.com/hyperionsolitude/Advanced_Video_Downreamer/discussions)

## 🔄 Changelog

### Version 1.0.0
- Initial release
- Basic download functionality
- File type detection
- Progress tracking
- Storage management
- Download status indicators
- Streamlined UI without audio-only filter

---

**Made with ❤️ for Android users who need a powerful, reliable download manager.**
