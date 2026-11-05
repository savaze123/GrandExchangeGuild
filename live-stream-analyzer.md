live-stream-analyzer

Real-time visual analysis of the Android UI using scrcpy and ffmpeg over ADB. Use when the user asks to monitor the screen, watch the app, or needs live visual context for automation and UI debugging.
Instructions
ADB Live Stream Analyzer
Summary

Provides continuous, real-time visual analysis of the Android UI without triggering performance drops or app crashes associated with repetitive adb shell screencap calls. Tailored for a Fedora Asahi Linux host connecting to an Android device.
When to Use

    The user asks the agent to "look at my screen," "watch the app," or "see live data."
    Real-time spatial or visual feedback is required for UI layout checks.
    The agent needs to monitor automation sequences (like Courier Guild) without interrupting the device's CPU with screenshot commands.

Prerequisites (Fedora Asahi Host)

Ensure the required packages are installed via dnf on the Asahi Linux host:

sudo dnf install scrcpy ffmpeg

Steps

    Start the Headless Video Stream: Run scrcpy in the background with a reduced bitrate and resolution to minimize overhead on both the Pixel Fold and the M1 host. Record directly to a stream file without opening a GUI window:

    scrcpy --max-size 1024 --video-bit-rate 2M --no-audio --record stream.mp4 &

    Verification: Ensure the process backgrounds successfully and stream.mp4 begins generating in the working directory.

    Frame Sampling: Use ffmpeg to extract the most recent frame from the active stream file at regular intervals. This pulls from the hardware-encoded stream rather than hitting the ADB shell:

    ffmpeg -y -sseof -1 -i stream.mp4 -update 1 -q:v 1 latest_frame.jpg

    Verification: Check that latest_frame.jpg is created and reflects the current screen state.

    Contextual UI Analysis: Ingest latest_frame.jpg alongside the UI XML tree to evaluate the current state of the app:

    adb shell uiautomator dump /sdcard/window_dump.xml
    adb pull /sdcard/window_dump.xml .

    Combine the visual frame and the XML hierarchy to understand UI occlusion, text wrapping, and live component states.

    Cleanup: When the task is complete or the user ends the session, kill the background stream and clean up artifacts:

    pkill scrcpy
    rm stream.mp4 latest_frame.jpg window_dump.xml

Gotchas

    ffmpeg may throw a minor warning when reading a file that is actively being written to by scrcpy; the -y and -sseof flags force it to grab the tail end of the stream regardless.
    Ensure no other scrcpy instances are running before starting the background record command to prevent port collisions.
