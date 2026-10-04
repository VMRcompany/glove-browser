const { spawn } = require("child_process");

let child = null;

function punctuate(text) {
  let value = String(text || "").replace(/\s+/g, " ").trim();
  if (!value) return "";
  value = value.replace(/\s+([,.;:!?])/g, "$1");
  if (!/[.!?…]$/.test(value)) value += ".";
  return value.charAt(0).toUpperCase() + value.slice(1);
}

function start(onText) {
  stop();
  const script = `
Add-Type -AssemblyName System.Speech
$engine = New-Object System.Speech.Recognition.SpeechRecognitionEngine
$engine.LoadGrammar((New-Object System.Speech.Recognition.DictationGrammar))
$engine.SetInputToDefaultAudioDevice()
$engine.InitialSilenceTimeout = [TimeSpan]::FromSeconds(8)
$engine.BabbleTimeout = [TimeSpan]::FromSeconds(2)
$engine.EndSilenceTimeout = [TimeSpan]::FromMilliseconds(900)
while ($true) {
  $result = $engine.Recognize()
  if ($null -eq $result) { continue }
  [Console]::Out.WriteLine($result.Text)
  [Console]::Out.Flush()
}
`;
  child = spawn("powershell.exe", ["-NoProfile", "-STA", "-Command", script], { windowsHide: true });
  let buffer = "";
  child.stdout.on("data", (chunk) => {
    buffer += chunk.toString("utf8");
    const lines = buffer.split(/\r?\n/);
    buffer = lines.pop() || "";
    lines.forEach((line) => {
      const text = punctuate(line);
      if (text) onText(text);
    });
  });
  child.on("exit", () => {
    child = null;
  });
}

function stop() {
  if (!child) return;
  try { child.kill(); } catch { /* already closed */ }
  child = null;
}

module.exports = { start, stop, punctuate };
