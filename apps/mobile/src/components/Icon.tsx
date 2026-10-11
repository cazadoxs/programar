import Svg, { Circle, Path, Rect } from "react-native-svg";
import { colors } from "../lib/theme";

/** Small line-icon set drawn on a 24×24 grid, 2px strokes, round caps. */
const PATHS = {
  sun: (
    <>
      <Circle cx={12} cy={12} r={4} />
      <Path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
    </>
  ),
  shield: <Path d="M12 3l7 3v5c0 4.6-3 8.4-7 10-4-1.6-7-5.4-7-10V6l7-3z" />,
  calendar: (
    <>
      <Rect x={3.5} y={5} width={17} height={15.5} rx={3} />
      <Path d="M3.5 10h17M8 3v4M16 3v4" />
    </>
  ),
  sparkles: <Path d="M12 3l1.8 4.7L18.5 9.5l-4.7 1.8L12 16l-1.8-4.7L5.5 9.5l4.7-1.8L12 3zM19 15l.8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8L19 15z" />,
  settings: (
    <>
      <Circle cx={12} cy={12} r={3} />
      <Path d="M19.4 15a1.7 1.7 0 00.3 1.8l.1.1a2 2 0 11-2.8 2.8l-.1-.1a1.7 1.7 0 00-1.8-.3 1.7 1.7 0 00-1 1.5V21a2 2 0 11-4 0v-.1a1.7 1.7 0 00-1.1-1.5 1.7 1.7 0 00-1.8.3l-.1.1a2 2 0 11-2.8-2.8l.1-.1a1.7 1.7 0 00.3-1.8 1.7 1.7 0 00-1.5-1H3a2 2 0 110-4h.1a1.7 1.7 0 001.5-1.1 1.7 1.7 0 00-.3-1.8l-.1-.1a2 2 0 112.8-2.8l.1.1a1.7 1.7 0 001.8.3H9a1.7 1.7 0 001-1.5V3a2 2 0 114 0v.1a1.7 1.7 0 001 1.5 1.7 1.7 0 001.8-.3l.1-.1a2 2 0 112.8 2.8l-.1.1a1.7 1.7 0 00-.3 1.8V9a1.7 1.7 0 001.5 1H21a2 2 0 110 4h-.1a1.7 1.7 0 00-1.5 1z" />
    </>
  ),
  plus: <Path d="M12 5v14M5 12h14" />,
  mic: (
    <>
      <Rect x={9} y={3} width={6} height={11} rx={3} />
      <Path d="M5 11a7 7 0 0014 0M12 18v3" />
    </>
  ),
  stop: <Rect x={6} y={6} width={12} height={12} rx={2.5} />,
  check: <Path d="M5 12.5l4.5 4.5L19 7.5" />,
  lock: (
    <>
      <Rect x={4.5} y={10.5} width={15} height={10} rx={2.5} />
      <Path d="M8 10.5V7.5a4 4 0 018 0v3" />
    </>
  ),
  clock: (
    <>
      <Circle cx={12} cy={12} r={9} />
      <Path d="M12 7v5l3 2" />
    </>
  ),
  bolt: <Path d="M13 2L4.5 13.5H11L10 22l8.5-11.5H12L13 2z" />,
  chevronRight: <Path d="M9 5l7 7-7 7" />,
  chevronLeft: <Path d="M15 5l-7 7 7 7" />,
  close: <Path d="M6 6l12 12M18 6L6 18" />,
  send: <Path d="M4 12l16-8-6 16-2.5-6.5L4 12z" />,
  book: <Path d="M4 5.5A2.5 2.5 0 016.5 3H20v15H6.5A2.5 2.5 0 004 20.5v-15zM4 20.5A2.5 2.5 0 006.5 23H20" />,
  tomato: (
    <>
      <Circle cx={12} cy={13.5} r={7.5} />
      <Path d="M12 6c0-1.5.8-2.5 2-3M9 6.5l3 1.5 3-1.5" />
    </>
  ),
  target: (
    <>
      <Circle cx={12} cy={12} r={9} />
      <Circle cx={12} cy={12} r={5} />
      <Circle cx={12} cy={12} r={1.2} />
    </>
  ),
  briefcase: (
    <>
      <Rect x={3} y={7} width={18} height={13} rx={2.5} />
      <Path d="M8.5 7V5.5A1.5 1.5 0 0110 4h4a1.5 1.5 0 011.5 1.5V7M3 12.5h18" />
    </>
  ),
  user: (
    <>
      <Circle cx={12} cy={8} r={4} />
      <Path d="M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6" />
    </>
  ),
  key: (
    <>
      <Circle cx={8} cy={15} r={4.5} />
      <Path d="M11.2 11.8L20 3M16.5 6.5l2.5 2.5M14 9l2 2" />
    </>
  ),
  refresh: <Path d="M20 11a8 8 0 00-14.3-4.9L4 8M4 4v4h4M4 13a8 8 0 0014.3 4.9L20 16M20 20v-4h-4" />,
  trash: <Path d="M4 7h16M9 7V4.5h6V7M6.5 7l1 13.5h9l1-13.5" />,
  star: <Path d="M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9L12 3z" />,
  phone: (
    <>
      <Rect x={6.5} y={2.5} width={11} height={19} rx={2.5} />
      <Path d="M11 18.5h2" />
    </>
  ),
  link: <Path d="M10 14a4.5 4.5 0 006.4 0l3-3a4.5 4.5 0 00-6.4-6.4l-1 1M14 10a4.5 4.5 0 00-6.4 0l-3 3a4.5 4.5 0 006.4 6.4l1-1" />,
  eyeOff: <Path d="M3 3l18 18M10.6 5.1A10 10 0 0112 5c6 0 9.5 7 9.5 7a17 17 0 01-3 3.9M6.6 6.6C3.9 8.4 2.5 12 2.5 12s3.5 7 9.5 7a9.6 9.6 0 005.4-1.6M9.9 9.9a3 3 0 004.2 4.2" />,
  flame: <Path d="M12 22c4 0 7-2.8 7-6.8 0-4.7-4.3-7.2-5-12.2-3.3 2.2-4.6 5.1-4.4 7.9C8.6 10 8 9 7.8 7.6 6 9.4 5 11.8 5 15.2 5 19.2 8 22 12 22z" />,
  inbox: <Path d="M3 13l3-8h12l3 8v6a1.5 1.5 0 01-1.5 1.5h-15A1.5 1.5 0 013 19v-6zM3 13h5l1.5 2.5h5L16 13h5" />,
  info: (
    <>
      <Circle cx={12} cy={12} r={9} />
      <Path d="M12 11v5.5M12 7.5v.5" />
    </>
  ),
} as const;

export type IconName = keyof typeof PATHS;

export function Icon({ name, size = 22, color = colors.text, strokeWidth = 2 }: { name: IconName; size?: number; color?: string; strokeWidth?: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round">
      {PATHS[name]}
    </Svg>
  );
}
