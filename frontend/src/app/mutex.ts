/**
 * A promise mutex, small enough not to warrant a dependency.
 *
 * Used to serialise token refresh: refresh tokens rotate, so two concurrent
 * refreshes would look like token reuse to the backend and revoke the session.
 */
export class Mutex {
  private locked = false
  private waiters: (() => void)[] = []

  isLocked(): boolean {
    return this.locked
  }

  async acquire(): Promise<() => void> {
    while (this.locked) {
      await new Promise<void>((resolve) => this.waiters.push(resolve))
    }
    this.locked = true

    let released = false
    return () => {
      if (released) return
      released = true
      this.locked = false
      const next = this.waiters.shift()
      if (next) next()
    }
  }

  async waitForUnlock(): Promise<void> {
    while (this.locked) {
      await new Promise<void>((resolve) => this.waiters.push(resolve))
    }
  }
}
