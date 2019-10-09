package com.github.unchuckable.jmush.model;

public enum Flag {
  ABODE('A'),
  BLIND('B'),
  CHOWN_OK('C'),
  DARK('D'),
  EXIT('E'),
  FLOATING('F'),
  GOING('G'),
  HAVEN('H'),
  INHERIT('I'),
  JUMP_OK('J'),
  KEY('K'),
  LINK_OK('L'),
  MONITOR('M'),
  NOSPOOF('N'),
  OPAQUE('O'),
  PLAYER('P'),
  QUIET('Q'),
  ROOM('R'),
  STICKY('S'),
  TRACE('T'),
  UNFINDABLE('U'),
  VISUAL('V'),
  WIZARD('W'),
  ANSI('X'),
  PARENT_OKAY('Y'),
  ROYALTY('Z'),
  AUDIBLE('a'),
  BOUNCE('b'),
  CONNECTED('c'),
  DESTROY_OK('d'),
  ENTER_OK('e'),
  FIXED('f'),
  UNINSPECTED('g'),
  HALTED('h'),
  IMMORTAL('i'),
  GAGGED('j'),
  CONSTANT('k'),
  LIGHT('l'),
  MYOPIC('m'),
  AUDITORIUM('n'),
  ZONE('o'),
  PUPPET('p'),
  TERSE('q'),
  ROBOT('r'),
  SAFE('s'),
  TRANSPARENT('t'),
  SUSPECT('u'),
  VERBOSE('v'),
  STAFF('w'),
  SLAVE('x'),
  CONTROL_OK('z'),
  STOP('!'),
  COMMANDS('$'),
  NOBLEED('-'),
  HTML('~'),
  HEAD('?'),
  VACATION('|'),
  WATCHER('+');
  
  private final char symbol;
  
  Flag( char symbol ) {
    this.symbol = symbol;
  }
  
  char getSymbol() {
    return this.symbol;
  }
  
  public static Flag forSymbol(char symbol) {
    for ( Flag thisFlag : Flag.values() ) {
      if ( thisFlag.getSymbol() == symbol ) {
        return thisFlag;
      }
    }
    return null;
  }
  
}
