# phobos-mips

> A modern JavaFX desktop interface for the MARS MIPS simulator. Same
> battle-tested assembler and CPU model that's been used in compilers
> classes for two decades, wrapped in a UI that doesn't feel like 2003.

Named after Phobos, the larger of Mars' two moons - this project orbits
MARS, reusing its assembler and simulator core while replacing the
Swing UI shell with a JavaFX one.

![scope](https://img.shields.io/badge/scope-lab%20MVP-lightgrey)
![java](https://img.shields.io/badge/JDK-17%2B-blue)
![javafx](https://img.shields.io/badge/JavaFX-21-blueviolet)
![license](https://img.shields.io/badge/license-MIT-green)

## What's reused vs. rewritten

| Layer                        | Origin                            |
| ---------------------------- | --------------------------------- |
| MIPS assembler               | MARS (`mars.assembler.*`)         |
| Simulator / pseudo-ops       | MARS (`mars.simulator.*`)         |
| Register file & memory       | MARS (`mars.mips.hardware.*`)     |
| Syscall I/O                  | MARS (`mars.util.SystemIO`)       |
| Backstep (undo)              | MARS (`mars.simulator.BackStepper`) |
| **Editor + UI shell**        | **New (JavaFX 21)**               |
| **Syntax highlighting**      | **New (RichTextFX + custom regex)** |
| **Autocomplete popup**       | **New, curated for the lab**      |
| **Toolbar / dark theme**     | **New (Catppuccin Mocha palette)**|

The original `Mars4_5.jar` ships in `libs/` and is pulled in by Gradle
as a flat-file dependency. None of the original Swing classes are
loaded at runtime - phobos draws its own UI from scratch.

## Layout

```
MARS-work/                          this project
├── build.gradle             Gradle build config
├── settings.gradle
├── libs/Mars4_5.jar         the original MARS, used as a library
├── run.command              double-click launcher (macOS)
├── LICENSE                  MIT
├── NOTICE                   third-party attribution
└── src/main/
    ├── java/modernmars/
    │   ├── App.java                     JavaFX entry point
    │   ├── core/
    │   │   ├── MarsBackend.java         adapter -> MARS classes
    │   │   └── ConsoleIO.java           captures stdin/stdout
    │   ├── ui/
    │   │   ├── MainWindow.java          shell + glue
    │   │   ├── EditorPane.java          RichTextFX code editor
    │   │   ├── MipsHighlighter.java     regex syntax highlighter
    │   │   ├── AutocompletePopup.java   keyword popup
    │   │   ├── RegistersPane.java       32 GPRs + PC, change-highlit
    │   │   ├── MemoryPane.java          16-word memory inspector
    │   │   ├── ConsolePane.java         program I/O console
    │   │   └── Toolbar.java             top action bar
    │   └── util/
    │       ├── MipsVocabulary.java      curated keyword catalogue
    │       └── RegisterNames.java
    └── resources/modernmars/
        └── theme-dark.css               Catppuccin Mocha theme
```

## Running

```bash
./gradlew run                # first run downloads JDK 17 + JavaFX
```

On macOS you can also double-click `run.command`.

The Gradle wrapper is committed, so you do not need to install Gradle
yourself. The build also uses the foojay toolchain resolver, so if you
do not have JDK 17 installed Gradle will download one automatically the
first time you run.

## Toolbar / shortcuts

| Action     | Glyph         | Shortcut        |
| ---------- | ------------- | --------------- |
| Open       | folder        | Cmd/Ctrl + O    |
| Save       | disk          | Cmd/Ctrl + S    |
| Assemble   | gear          | F3              |
| Reset      | circular arrow | F6             |
| Run        | play triangle | F5              |
| Step       | dashed arrow  | F7              |
| Backstep   | left arrow    | F8              |

After every step / run / backstep the registers and memory panels
refresh from the simulator; the registers panel highlights any rows
whose values changed since the previous refresh in peach.

Memory defaults to base address `0x10010000` (the start of the user
data segment); type any 8-digit hex value into the address field and
press Enter to jump elsewhere.

## Autocomplete

The editor has a Sublime/VS Code-style popup that surfaces curated MIPS
keywords as you type. Triggers automatically at word boundaries; press
Cmd/Ctrl-Space to force it open. The catalogue covers:

- Every instruction in `mipsasmtable.jpg` and `MIPSLab_2023.pdf`
  (arithmetic, logical, shifts, memory, branches, jumps, syscall,
  pseudo-instructions like `li`, `la`, `move`, `blt`, `bgt`, `ble`,
  `bge`)
- All conventional registers (`$zero` .. `$t0` .. `$sp`, `$ra`)
- Common assembler directives (`.text`, `.data`, `.globl`, `.asciiz`,
  `.word`, `.byte`, `.space`, `.align`)
- Syscall snippets - typing `print_int`, `print_string`, `read_int`,
  `read_string`, `alloc`, or `exit` expands to the canonical
  `li $v0, N` + `syscall` pair from the lab handout

## What's currently in scope

Implemented:

- Code editor with line numbers, MIPS syntax highlighting, autocomplete
- Open / Save .asm files
- Assemble (extended assembler / pseudo-ops enabled by default)
- Run to termination, single-step, backstep (undo last instruction),
  reset
- 32 general-purpose registers + PC, hex display, changed rows
  highlighted on every step
- Memory inspector (16 consecutive words)
- Console showing simulator stdout, with input field for `read_int`,
  `read_string`, etc., and a Clear button
- Diagnostics (errors and warnings) printed to the console with file,
  line, and column

Deliberately not in v1 (in priority order, if you want to extend):

- Breakpoints in the gutter
- Multi-file tabs / project mode
- Find / replace
- Run-speed slider
- Floating-point register pane (Coprocessor 1)
- Tools menu (Bitmap Display, Cache Simulator, Keyboard MMIO, etc.)
- Settings dialog (sensible defaults are baked in)
- Symbol-table viewer

## Credits

phobos-mips is a fresh UI built **on top of** MARS. All of the hard
work - the assembler, the simulator, the syscall handling, the pseudo-
instruction expansion - is the work of:

- **Pete Sanderson** (Otterbein University) - psanderson@otterbein.edu
- **Kenneth Vollmar** (Missouri State University) - kenvollmar@missouristate.edu

MARS itself: <https://courses.missouristate.edu/KenVollmar/MARS/>
MARS source mirror: <https://github.com/dpetersanderson/MARS>

The included `libs/Mars4_5.jar` is the unmodified MARS 4.5 release.

## License

phobos-mips itself is MIT - see `LICENSE`.

Bundled third-party components (MARS, JavaFX, RichTextFX) retain their
own licenses; full text is recorded in `NOTICE`.
