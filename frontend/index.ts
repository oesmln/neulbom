import { registerRootComponent } from "expo";
import Boot from "./src/Boot";

// Boot applies the stored display settings (dark mode, text scale) and only
// then requires App — see src/Boot.tsx for why the indirection exists.
registerRootComponent(Boot);
