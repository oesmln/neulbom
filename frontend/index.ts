import { registerRootComponent } from "expo";
import Boot from "./src/Boot";

// Boot applies stored display settings and installs runtime style refresh before
// requiring App.
registerRootComponent(Boot);
