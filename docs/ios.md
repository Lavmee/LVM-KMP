# iOS

This guide shows how an iOS app runs a component tree of `lvm-decompose`: the framework that Swift imports, the root of the tree, saved state, back gestures, and SwiftUI views that follow Decompose values. It builds on [Decompose's documentation](https://arkivanov.github.io/Decompose/), which covers components and navigation themselves.

## The framework

Swift reaches `lvm-decompose` through the framework of the app's shared module, which `lvm { targets { ios { framework { ... } } } }` creates. Swift also names some types of Decompose and Essenty, so the framework exports all of them, and each exported library is an `api` dependency of the module:

```kotlin
// shared/build.gradle.kts
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

kotlin {
    sourceSets.commonMain.dependencies {
        api("tech.annexflow.lvm:lvm-decompose:x.y.z")
        api(lvmLibs.decompose)
        api(lvmLibs.essenty.lifecycle)
        api(lvmLibs.essenty.state.keeper)
        api(lvmLibs.essenty.back.handler)
    }
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.withType<Framework>().configureEach {
            export("tech.annexflow.lvm:lvm-decompose:x.y.z")
            export(lvmLibs.decompose)
            export(lvmLibs.essenty.lifecycle)
            export(lvmLibs.essenty.state.keeper)
            export(lvmLibs.essenty.back.handler)
        }
    }
}
```

In Swift, classes keep their Kotlin names, such as `AppComponentRoot` and `SerializableContainer`. Top-level functions belong to a class named after their file, such as `NSCodersKt`. A class nested in a class is reached with a dot; one nested in an interface, which Swift sees as a protocol, takes the interface's name as a prefix, so `RootComponent.Child.List` is `RootComponentChild.List`. Kotlin default arguments don't exist in Swift, so every argument is passed.

## The root

`AppComponentRoot` holds the root context of the tree. Create one for the app's lifetime in the app delegate and build the root component on its `context`:

```swift
class AppDelegate: NSObject, UIApplicationDelegate {
    private var savedState: SerializableContainer? = nil

    lazy var root = AppComponentRoot(logger: AppLoggerKt.appLogger(), savedState: savedState)
    lazy var rootComponent: RootComponent = DefaultRootComponent(context: root.context)
}

@main
struct ExampleApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            RootView(appDelegate.rootComponent)
        }
    }
}
```

`AppLoggerKt.appLogger()` stands for a Kotlin function of the shared module that returns the app's `Logger`, for example `consoleLogger()` from `lvm-logger-console`. `DefaultRootComponent` is the app's own root component, which delegates to the context it receives.

The root follows Essenty's `ApplicationLifecycle`: its components are resumed while the app is in the foreground and stopped in the background. A second constructor, `AppComponentRoot(logger:savedState:lifecycle:)`, takes another `Lifecycle`, such as a `LifecycleRegistry` in tests.

## Saved state

When the app delegate opts in to secure state restoration, UIKit hands it an `NSCoder` to save the app's state and another to restore it. `encodeSavedState` writes the state of the tree into the coder, and `decodeSavedState` reads it back before the lazy root is created:

```swift
func application(_ application: UIApplication, shouldSaveSecureApplicationState coder: NSCoder) -> Bool {
    NSCodersKt.encodeSavedState(coder, state: root.saveState())
    return true
}

func application(_ application: UIApplication, shouldRestoreSecureApplicationState coder: NSCoder) -> Bool {
    savedState = NSCodersKt.decodeSavedState(coder)
    return true
}
```

Add both functions to the app delegate. When the coder holds no state, or state that can't be read, `decodeSavedState` returns `nil` and the app starts fresh.

## Back gestures

Components register their back handling with the tree, for example through `childStack` with `handleBackButton = true`. The root's `backDispatcher` receives the app's back gestures: `back()` passes one to the components and returns whether one of them handled it, and `isEnabled` tells whether one would. Forward the gesture of your navigation to it, for example a swipe from the left edge with the `onEdgeSwipeBack` helper below:

```swift
RootView(appDelegate.rootComponent)
    .onEdgeSwipeBack { _ = appDelegate.root.backDispatcher.back() }
```

An app with Compose Multiplatform passes `root.backDispatcher` to Decompose's `PredictiveBackGestureOverlay` instead.

## SwiftUI helpers

These helpers are not part of a library. Copy them into the app and change them as needed.

```swift
import SwiftUI
import Shared

/// Publishes the current value of a Decompose `Value` to SwiftUI.
final class ObservableValue<T: AnyObject>: ObservableObject {
    @Published private(set) var value: T
    private var cancellation: Cancellation?

    init(_ value: Value<T>) {
        self.value = value.value
        cancellation = value.subscribe { [weak self] newValue in self?.value = newValue }
    }

    deinit {
        cancellation?.cancel()
    }
}

/// A view property that follows a Decompose `Value`.
@propertyWrapper
struct StateValue<T: AnyObject>: DynamicProperty {
    @ObservedObject private var observable: ObservableValue<T>

    var wrappedValue: T { observable.value }

    init(_ value: Value<T>) {
        observable = ObservableValue(value)
    }
}

/// A `MutableValue` for previews.
func mutableValue<T: AnyObject>(_ initialValue: T) -> MutableValue<T> {
    MutableValueBuilderKt.MutableValue(initialValue: initialValue) as! MutableValue<T>
}

/// A stack with one child, for previews.
func simpleChildStack<T: AnyObject>(_ instance: T) -> ChildStack<AnyObject, T> {
    ChildStack(configuration: "Preview" as NSString, instance: instance)
}

extension View {
    /// Calls `action` when the user swipes right from the left edge.
    func onEdgeSwipeBack(_ action: @escaping () -> Void) -> some View {
        gesture(
            DragGesture(minimumDistance: 20).onEnded { drag in
                if drag.startLocation.x < 24 && drag.translation.width > 80 {
                    action()
                }
            }
        )
    }
}
```

`StateValue` redraws a view when a `Value` changes, for example the stack of the root component:

```swift
struct RootView: View {
    @StateValue private var stack: ChildStack<AnyObject, RootComponentChild>

    init(_ component: RootComponent) {
        _stack = StateValue(component.stack)
    }

    var body: some View {
        switch stack.active.instance {
        case let child as RootComponentChild.List: ListView(child.component)
        default: EmptyView()
        }
    }
}
```

`RootComponent.Child` is a sealed class here, not a sealed interface: Swift sees a type argument that is a Kotlin interface as `AnyObject`.
