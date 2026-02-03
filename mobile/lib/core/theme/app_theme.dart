import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class AppTheme {
  AppTheme._();

  // -------------------------------------------------------
  // Brand colours
  // -------------------------------------------------------
  static const Color _black = Color(0xFF000000);
  static const Color _white = Color(0xFFFFFFFF);
  static const Color _grey50 = Color(0xFFFAFAFA);
  static const Color _grey100 = Color(0xFFF5F5F5);
  static const Color _grey200 = Color(0xFFEEEEEE);
  static const Color _grey300 = Color(0xFFE0E0E0);
  static const Color _grey400 = Color(0xFFBDBDBD);
  static const Color _grey500 = Color(0xFF9E9E9E);
  static const Color _grey600 = Color(0xFF757575);
  static const Color _grey700 = Color(0xFF616161);
  static const Color _grey800 = Color(0xFF424242);
  static const Color _grey900 = Color(0xFF212121);

  static const Color successGreen = Color(0xFF2E7D32);
  static const Color errorRed = Color(0xFFC62828);
  static const Color warningAmber = Color(0xFFF9A825);
  static const Color infoBlue = Color(0xFF1565C0);

  // -------------------------------------------------------
  // Typography helpers
  // -------------------------------------------------------
  static TextTheme _buildTextTheme(TextTheme base, Color colour) {
    return GoogleFonts.interTextTheme(base).copyWith(
      displayLarge: GoogleFonts.inter(
        fontSize: 32,
        fontWeight: FontWeight.w700,
        color: colour,
        letterSpacing: -0.5,
      ),
      displayMedium: GoogleFonts.inter(
        fontSize: 28,
        fontWeight: FontWeight.w700,
        color: colour,
        letterSpacing: -0.5,
      ),
      displaySmall: GoogleFonts.inter(
        fontSize: 24,
        fontWeight: FontWeight.w600,
        color: colour,
      ),
      headlineLarge: GoogleFonts.inter(
        fontSize: 22,
        fontWeight: FontWeight.w600,
        color: colour,
      ),
      headlineMedium: GoogleFonts.inter(
        fontSize: 20,
        fontWeight: FontWeight.w600,
        color: colour,
      ),
      headlineSmall: GoogleFonts.inter(
        fontSize: 18,
        fontWeight: FontWeight.w600,
        color: colour,
      ),
      titleLarge: GoogleFonts.inter(
        fontSize: 16,
        fontWeight: FontWeight.w600,
        color: colour,
      ),
      titleMedium: GoogleFonts.inter(
        fontSize: 14,
        fontWeight: FontWeight.w600,
        color: colour,
      ),
      titleSmall: GoogleFonts.inter(
        fontSize: 12,
        fontWeight: FontWeight.w600,
        color: colour,
      ),
      bodyLarge: GoogleFonts.inter(
        fontSize: 16,
        fontWeight: FontWeight.w400,
        color: colour,
      ),
      bodyMedium: GoogleFonts.inter(
        fontSize: 14,
        fontWeight: FontWeight.w400,
        color: colour,
      ),
      bodySmall: GoogleFonts.inter(
        fontSize: 12,
        fontWeight: FontWeight.w400,
        color: colour.withOpacity(0.7),
      ),
      labelLarge: GoogleFonts.inter(
        fontSize: 14,
        fontWeight: FontWeight.w600,
        color: colour,
        letterSpacing: 0.5,
      ),
      labelMedium: GoogleFonts.inter(
        fontSize: 12,
        fontWeight: FontWeight.w500,
        color: colour,
      ),
      labelSmall: GoogleFonts.inter(
        fontSize: 10,
        fontWeight: FontWeight.w500,
        color: colour.withOpacity(0.6),
      ),
    );
  }

  // -------------------------------------------------------
  // Button styles
  // -------------------------------------------------------
  static ElevatedButtonThemeData _elevatedButtonTheme({
    required Color background,
    required Color foreground,
  }) {
    return ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: background,
        foregroundColor: foreground,
        elevation: 0,
        minimumSize: const Size(double.infinity, 52),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(8),
        ),
        textStyle: GoogleFonts.inter(
          fontSize: 16,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }

  static OutlinedButtonThemeData _outlinedButtonTheme({
    required Color borderColour,
    required Color foreground,
  }) {
    return OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: foreground,
        elevation: 0,
        minimumSize: const Size(double.infinity, 52),
        side: BorderSide(color: borderColour, width: 1.5),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(8),
        ),
        textStyle: GoogleFonts.inter(
          fontSize: 16,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }

  static TextButtonThemeData _textButtonTheme({required Color foreground}) {
    return TextButtonThemeData(
      style: TextButton.styleFrom(
        foregroundColor: foreground,
        textStyle: GoogleFonts.inter(
          fontSize: 14,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }

  // -------------------------------------------------------
  // Input decoration
  // -------------------------------------------------------
  static InputDecorationTheme _inputDecorationTheme({
    required Color borderColour,
    required Color focusedBorder,
    required Color errorColour,
    required Color fillColour,
    required Color textColour,
    required Color hintColour,
  }) {
    final border = OutlineInputBorder(
      borderRadius: BorderRadius.circular(8),
      borderSide: BorderSide(color: borderColour, width: 1.5),
    );

    return InputDecorationTheme(
      filled: true,
      fillColor: fillColour,
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      border: border,
      enabledBorder: border,
      focusedBorder: border.copyWith(
        borderSide: BorderSide(color: focusedBorder, width: 2),
      ),
      errorBorder: border.copyWith(
        borderSide: BorderSide(color: errorColour, width: 1.5),
      ),
      focusedErrorBorder: border.copyWith(
        borderSide: BorderSide(color: errorColour, width: 2),
      ),
      hintStyle: GoogleFonts.inter(
        fontSize: 14,
        fontWeight: FontWeight.w400,
        color: hintColour,
      ),
      labelStyle: GoogleFonts.inter(
        fontSize: 14,
        fontWeight: FontWeight.w500,
        color: textColour,
      ),
      errorStyle: GoogleFonts.inter(
        fontSize: 12,
        color: errorColour,
      ),
    );
  }

  // -------------------------------------------------------
  // Card theme
  // -------------------------------------------------------
  static CardTheme _cardTheme({
    required Color colour,
    required Color shadowColour,
  }) {
    return CardTheme(
      color: colour,
      elevation: 0,
      shadowColor: shadowColour,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(color: shadowColour.withOpacity(0.12), width: 1),
      ),
      margin: const EdgeInsets.symmetric(horizontal: 0, vertical: 6),
    );
  }

  // -------------------------------------------------------
  // LIGHT THEME  (white background, black accents)
  // -------------------------------------------------------
  static ThemeData get light {
    final base = ThemeData.light(useMaterial3: true);
    final textTheme = _buildTextTheme(base.textTheme, _black);

    return base.copyWith(
      brightness: Brightness.light,
      primaryColor: _black,
      scaffoldBackgroundColor: _white,
      colorScheme: const ColorScheme.light(
        primary: _black,
        onPrimary: _white,
        secondary: _grey800,
        onSecondary: _white,
        surface: _white,
        onSurface: _black,
        error: errorRed,
        onError: _white,
      ),
      textTheme: textTheme,
      appBarTheme: AppBarTheme(
        backgroundColor: _white,
        foregroundColor: _black,
        elevation: 0,
        scrolledUnderElevation: 0.5,
        centerTitle: false,
        titleTextStyle: GoogleFonts.inter(
          fontSize: 18,
          fontWeight: FontWeight.w700,
          color: _black,
        ),
        iconTheme: const IconThemeData(color: _black),
      ),
      bottomNavigationBarTheme: const BottomNavigationBarThemeData(
        backgroundColor: _white,
        selectedItemColor: _black,
        unselectedItemColor: _grey500,
        type: BottomNavigationBarType.fixed,
        elevation: 0,
      ),
      elevatedButtonTheme: _elevatedButtonTheme(
        background: _black,
        foreground: _white,
      ),
      outlinedButtonTheme: _outlinedButtonTheme(
        borderColour: _black,
        foreground: _black,
      ),
      textButtonTheme: _textButtonTheme(foreground: _black),
      inputDecorationTheme: _inputDecorationTheme(
        borderColour: _grey300,
        focusedBorder: _black,
        errorColour: errorRed,
        fillColour: _grey50,
        textColour: _black,
        hintColour: _grey500,
      ),
      cardTheme: _cardTheme(colour: _white, shadowColour: _grey300),
      dividerTheme: const DividerThemeData(
        color: _grey200,
        thickness: 1,
        space: 1,
      ),
      bottomSheetTheme: const BottomSheetThemeData(
        backgroundColor: _white,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
        ),
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: _grey900,
        contentTextStyle: GoogleFonts.inter(color: _white, fontSize: 14),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  // -------------------------------------------------------
  // DARK THEME  (black background, white accents)
  // -------------------------------------------------------
  static ThemeData get dark {
    final base = ThemeData.dark(useMaterial3: true);
    final textTheme = _buildTextTheme(base.textTheme, _white);

    return base.copyWith(
      brightness: Brightness.dark,
      primaryColor: _white,
      scaffoldBackgroundColor: _black,
      colorScheme: const ColorScheme.dark(
        primary: _white,
        onPrimary: _black,
        secondary: _grey300,
        onSecondary: _black,
        surface: _grey900,
        onSurface: _white,
        error: errorRed,
        onError: _white,
      ),
      textTheme: textTheme,
      appBarTheme: AppBarTheme(
        backgroundColor: _black,
        foregroundColor: _white,
        elevation: 0,
        scrolledUnderElevation: 0.5,
        centerTitle: false,
        titleTextStyle: GoogleFonts.inter(
          fontSize: 18,
          fontWeight: FontWeight.w700,
          color: _white,
        ),
        iconTheme: const IconThemeData(color: _white),
      ),
      bottomNavigationBarTheme: const BottomNavigationBarThemeData(
        backgroundColor: _black,
        selectedItemColor: _white,
        unselectedItemColor: _grey600,
        type: BottomNavigationBarType.fixed,
        elevation: 0,
      ),
      elevatedButtonTheme: _elevatedButtonTheme(
        background: _white,
        foreground: _black,
      ),
      outlinedButtonTheme: _outlinedButtonTheme(
        borderColour: _white,
        foreground: _white,
      ),
      textButtonTheme: _textButtonTheme(foreground: _white),
      inputDecorationTheme: _inputDecorationTheme(
        borderColour: _grey700,
        focusedBorder: _white,
        errorColour: errorRed,
        fillColour: _grey900,
        textColour: _white,
        hintColour: _grey500,
      ),
      cardTheme: _cardTheme(colour: _grey900, shadowColour: _grey800),
      dividerTheme: const DividerThemeData(
        color: _grey800,
        thickness: 1,
        space: 1,
      ),
      bottomSheetTheme: BottomSheetThemeData(
        backgroundColor: _grey900,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
        ),
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: _white,
        contentTextStyle: GoogleFonts.inter(color: _black, fontSize: 14),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }
}
